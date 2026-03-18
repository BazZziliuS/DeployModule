package pro.gravit.launchermodules.deploymodule;

import com.google.gson.JsonSyntaxException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pro.gravit.launcher.base.Launcher;
import pro.gravit.launcher.base.profiles.ClientProfile;
import pro.gravit.launchserver.LaunchServer;
import pro.gravit.launchserver.auth.profiles.LocalProfileProvider;
import pro.gravit.launchserver.socket.NettyConnectContext;
import pro.gravit.launchserver.socket.handlers.NettyWebAPIHandler;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class UploadProfileHandler implements NettyWebAPIHandler.SimpleSeverletHandler {
    private static final Logger logger = LogManager.getLogger(UploadProfileHandler.class);
    private final LaunchServer server;
    private final DeployModuleConfig config;

    public UploadProfileHandler(LaunchServer server, DeployModuleConfig config) {
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

        if (request.content().readableBytes() == 0) {
            sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.BAD_REQUEST,
                    ApiResponse.error("Empty request body")));
            return;
        }

        String body = request.content().toString(StandardCharsets.UTF_8);
        ClientProfile profile;
        try {
            profile = Launcher.gsonManager.gson.fromJson(body, ClientProfile.class);
        } catch (JsonSyntaxException e) {
            sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.BAD_REQUEST,
                    ApiResponse.error("Invalid JSON: " + e.getMessage())));
            return;
        }

        try {
            profile.verify();
        } catch (IllegalArgumentException e) {
            sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.BAD_REQUEST,
                    ApiResponse.error("Profile verification failed: " + e.getMessage())));
            return;
        }

        String title = profile.getTitle();
        if (!token.isProfileAllowed(title)) {
            sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.FORBIDDEN,
                    ApiResponse.error("Token does not have access to profile: " + title)));
            return;
        }

        if (!(server.config.profileProvider instanceof LocalProfileProvider localProvider)) {
            sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    ApiResponse.error("Only LocalProfileProvider is supported")));
            return;
        }

        String dir = profile.getDir();
        Path profilePath = server.dir.resolve(localProvider.profilesDir).resolve(dir + ".json");
        try (Writer writer = Files.newBufferedWriter(profilePath, StandardCharsets.UTF_8)) {
            writer.write(body);
        } catch (IOException e) {
            logger.error("Failed to write profile file", e);
            sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    ApiResponse.error("Failed to write profile: " + e.getMessage())));
            return;
        }

        try {
            server.syncProfilesDir();
        } catch (IOException e) {
            logger.error("Failed to sync profiles after upload", e);
            sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    ApiResponse.error("Profile saved but sync failed: " + e.getMessage())));
            return;
        }

        logger.info("Profile '{}' uploaded successfully", title);
        sendHttpResponse(ctx, simpleJsonResponse(HttpResponseStatus.OK,
                ApiResponse.ok("Profile '" + title + "' uploaded successfully")));
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
