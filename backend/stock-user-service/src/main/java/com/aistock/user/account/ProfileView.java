package com.aistock.user.account;
import java.util.UUID;
/** Safe account projection; credentials and session tokens are intentionally absent. */
public record ProfileView(UUID userId,String email,String nickname,String role,String status,
        boolean emailVerified,boolean experimentAllowed,long revision,String avatarStyle) {
    public ProfileView(UUID userId,String email,String nickname,String role,String status,
                       boolean emailVerified,boolean experimentAllowed,long revision) {
        this(userId,email,nickname,role,status,emailVerified,experimentAllowed,revision,null);
    }
}
