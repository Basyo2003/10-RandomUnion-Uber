package com.team10.uber.payment.security.chain;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AuthContext {

    private final HttpServletRequest request;
    private final HttpServletResponse response;

    private String token;
    private Claims claims;
    private String userEmail;
    private Long userId;
    private String role;
    private boolean passed = false;
    private int errorStatus = 0;
    private String errorMessage = "";

    public AuthContext(HttpServletRequest request, HttpServletResponse response) {
        this.request  = request;
        this.response = response;
    }

    public HttpServletRequest  getRequest()      { return request; }
    public HttpServletResponse getResponse()     { return response; }
    public String   getToken()                   { return token; }
    public Claims   getClaims()                  { return claims; }
    public String   getUserEmail()               { return userEmail; }
    public Long     getUserId()                  { return userId; }
    public String   getRole()                    { return role; }
    public boolean  isPassed()                   { return passed; }
    public int      getErrorStatus()             { return errorStatus; }
    public String   getErrorMessage()            { return errorMessage; }

    public void setToken(String token)           { this.token = token; }
    public void setClaims(Claims claims)         { this.claims = claims; }
    public void setUserEmail(String email)       { this.userEmail = email; }
    public void setUserId(Long userId)           { this.userId = userId; }
    public void setRole(String role)             { this.role = role; }
    public void setPassed(boolean passed)        { this.passed = passed; }
    public void fail(int status, String msg)     { this.errorStatus = status; this.errorMessage = msg; }
}
