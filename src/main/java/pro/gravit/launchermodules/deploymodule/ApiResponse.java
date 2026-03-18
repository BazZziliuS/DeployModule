package pro.gravit.launchermodules.deploymodule;

public class ApiResponse {
    public String error;
    public String message;

    public ApiResponse(String error, String message) {
        this.error = error;
        this.message = message;
    }

    public static ApiResponse ok(String message) {
        return new ApiResponse(null, message);
    }

    public static ApiResponse error(String error) {
        return new ApiResponse(error, null);
    }
}
