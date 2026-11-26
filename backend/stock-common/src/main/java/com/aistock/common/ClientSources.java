package com.aistock.common;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/** Trust source overrides only across explicitly authenticated/known proxy boundaries. */
public final class ClientSources {
 private ClientSources(){}
 public static String gateway(HttpServletRequest request,String trustedProxyHost){
  String peer=request.getRemoteAddr();String supplied=ip(request.getHeader("X-Real-IP"));
  if(supplied!=null&&trustedProxyHost!=null&&!trustedProxyHost.isBlank())try{
   if(Arrays.stream(InetAddress.getAllByName(trustedProxyHost)).anyMatch(address->address.getHostAddress().equals(ip(peer))))return supplied;
  }catch(java.net.UnknownHostException unavailable){/* Fail closed to the connection peer. */}
  return peer;
 }
 public static String user(HttpServletRequest request,String serviceToken){
  String provided=request.getHeader("X-Service-Token");String source=ip(request.getHeader("X-Auth-Source"));
  if(source!=null&&serviceToken!=null&&!serviceToken.isBlank()&&provided!=null
    &&MessageDigest.isEqual(serviceToken.getBytes(StandardCharsets.UTF_8),provided.getBytes(StandardCharsets.UTF_8)))return source;
  return request.getRemoteAddr();
 }
 private static String ip(String value){
  if(value==null||value.length()>64||(!value.contains(".")&&!value.contains(":"))||!value.matches("[0-9a-fA-F:.]{3,64}"))return null;
  try{return InetAddress.getByName(value).getHostAddress();}catch(java.net.UnknownHostException invalid){return null;}
 }
}
