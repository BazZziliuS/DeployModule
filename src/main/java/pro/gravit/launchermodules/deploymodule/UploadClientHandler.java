package pro.gravit.launchermodules.deploymodule;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pro.gravit.launchserver.LaunchServer;
import pro.gravit.launchserver.auth.profiles.LocalProfileProvider;
import pro.gravit.launchserver.socket.NettyConnectContext;
import pro.gravit.launchserver.socket.handlers.NettyWebAPIHandler;
import pro.gravit.utils.helper.IOHelper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UploadClientHandler implements NettyWebAPIHandler.SimpleSeverletHandler {
    private static final Logger logger = LogManager.getLogger(UploadClientHandler.class);
    private final LaunchServer server;
    private final DeployModuleConfig config;

    public UploadClientHandler(LaunchServer server, DeployModuleConfig config) {
        this.server = server;
        this.config = config;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request, NettyConnectContext context) {
        if (!request.method().equals(HttpMethod.POST)) {
            sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.METHOD_NOT_ALLOWED,
                    ApiResponse.error("Method not allowed")));
            return;
        }

        String tokenValue = extractToken(request);
        if (tokenValue == null) {
            sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.UNAUTHORIZED,
                    ApiResponse.error("Token is required")));
            return;
        }

        DeployModuleConfig.DeployToken token = config.findToken(tokenValue);
        if (token == null) {
            sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.FORBIDDEN,
                    ApiResponse.error("Invalid token")));
            return;
        }

        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        List<String> nameParams = decoder.parameters().get("name");
        if (nameParams == null || nameParams.isEmpty()) {
            sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.BAD_REQUEST,
                    ApiResponse.error("Query parameter 'name' is required")));
            return;
        }

        String name = nameParams.get(0);
        if (!isValidName(name)) {
            sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.BAD_REQUEST,
                    ApiResponse.error("Invalid client name: must not contain '..' or path separators")));
            return;
        }

        if (!token.isClientAllowed(name)) {
            sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.FORBIDDEN,
                    ApiResponse.error("Token does not have access to client: " + name)));
            return;
        }

        if (!(server.config.profileProvider instanceof LocalProfileProvider)) {
            sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    ApiResponse.error("Only LocalProfileProvider is supported")));
            return;
        }

        ByteBuf content = request.content();
        if (content.readableBytes() == 0) {
            sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.BAD_REQUEST,
                    ApiResponse.error("Empty request body")));
            return;
        }

        Path clientDir = server.updatesDir.resolve(name);

        try {
            // Remove existing directory
            if (Files.exists(clientDir)) {
                IOHelper.deleteDir(clientDir, true);
            }
            Files.createDirectories(clientDir);

            // Extract ZIP
            byte[] zipData = new byte[content.readableBytes()];
            content.readBytes(zipData);

            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if (entryName.contains("..")) {
                        IOHelper.deleteDir(clientDir, true);
                        sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.BAD_REQUEST,
                                ApiResponse.error("Zip entry contains path traversal: " + entryName)));
                        return;
                    }
                    Path entryPath = clientDir.resolve(entryName).normalize();
                    if (!entryPath.startsWith(clientDir)) {
                        IOHelper.deleteDir(clientDir, true);
                        sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.BAD_REQUEST,
                                ApiResponse.error("Zip entry escapes target directory: " + entryName)));
                        return;
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        try (var out = Files.newOutputStream(entryPath)) {
                            zis.transferTo(out);
                        }
                    }
                    zis.closeEntry();
                }
            }
        } catch (IOException e) {
            logger.error("Failed to extract ZIP for client '{}'", name, e);
            sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    ApiResponse.error("Failed to process client: " + e.getMessage())));
            return;
        }

        // Sync hashes
        try {
            server.syncUpdatesDir(List.of(name));
        } catch (IOException e) {
            logger.error("Failed to sync client '{}' after upload", name, e);
            sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    ApiResponse.error("Client extracted but sync failed: " + e.getMessage())));
            return;
        }

        logger.info("Client '{}' uploaded successfully", name);
        sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.OK,
                ApiResponse.ok("Client '" + name + "' uploaded successfully")));
    }

    private boolean isValidName(String name) {
        if (name == null || name.isEmpty()) return false;
        return !name.contains("..") && !name.contains("/") && !name.contains("\\");
    }

    private String extractToken(FullHttpRequest request) {
        String headerToken = request.headers().get("X-Upload-Token");
        if (headerToken != null && !headerToken.isEmpty()) return headerToken;

        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        List<String> tokenParams = decoder.parameters().get("token");
        if (tokenParams != null && !tokenParams.isEmpty()) return tokenParams.get(0);
        return null;
    }
}
