package pro.gravit.launchermodules.deploymodule;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class DeployModuleConfig {
    public boolean enabled = true;
    public List<DeployToken> tokens;

    public DeployToken findToken(String value) {
        if (value == null || value.isEmpty()) return null;
        for (DeployToken t : tokens) {
            if (t.token.equals(value)) return t;
        }
        return null;
    }

    public static DeployModuleConfig getDefault() {
        DeployModuleConfig config = new DeployModuleConfig();
        config.enabled = true;
        DeployToken defaultToken = new DeployToken();
        defaultToken.token = UUID.randomUUID().toString();
        defaultToken.allowedProfiles = List.of();
        defaultToken.allowedClients = List.of();
        config.tokens = List.of(defaultToken);
        return config;
    }

    public static class DeployToken {
        public String token;
        public List<String> allowedProfiles;
        public List<String> allowedClients;

        public boolean isProfileAllowed(String name) {
            return isAllowed(name, allowedProfiles);
        }

        public boolean isClientAllowed(String name) {
            return isAllowed(name, allowedClients);
        }

        private boolean isAllowed(String name, List<String> patterns) {
            if (patterns == null || patterns.isEmpty()) return true;
            for (String pattern : patterns) {
                if (matchGlob(pattern, name)) return true;
            }
            return false;
        }

        private boolean matchGlob(String pattern, String value) {
            StringBuilder regex = new StringBuilder("^");
            for (int i = 0; i < pattern.length(); i++) {
                char c = pattern.charAt(i);
                if (c == '*') {
                    regex.append(".*");
                } else {
                    regex.append(Pattern.quote(String.valueOf(c)));
                }
            }
            regex.append("$");
            return value.matches(regex.toString());
        }
    }
}
