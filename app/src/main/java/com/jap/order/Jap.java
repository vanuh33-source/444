package com.jap.order;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Jap {
    public static final String API = "https://justanotherpanel.com/api/v2";

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("jap", Context.MODE_PRIVATE);
    }
    public static String key(Context c) { return prefs(c).getString("apiKey", ""); }
    public static String service(Context c) { return prefs(c).getString("service", "8565"); }
    public static String comments(Context c) { return prefs(c).getString("comments", ""); }

    public static String igUrl(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("(?:https?://)?(?:www\\.)?instagram\\.com/(p|reel|reels|tv)/([A-Za-z0-9_-]+)").matcher(text);
        if (!m.find()) return null;
        String type = m.group(1);
        if (type.equals("reels")) type = "reel";
        return "https://www.instagram.com/" + type + "/" + m.group(2) + "/";
    }

    public static String enc(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }

    public static String post(String body) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(API).openConnection();
            con.setRequestMethod("POST");
            con.setConnectTimeout(20000);
            con.setReadTimeout(30000);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            OutputStream os = con.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
            int code = con.getResponseCode();
            InputStream is = code < 400 ? con.getInputStream() : con.getErrorStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return "{\"error\":\"네트워크 오류: " + e.getMessage() + "\"}";
        } finally {
            if (con != null) con.disconnect();
        }
    }

    public static String order(Context c, String link) {
        String k = key(c), s = service(c), cm = comments(c);
        if (k.isEmpty()) return "API 키가 없습니다 (앱에서 설정)";
        if (!s.matches("\\d+")) return "서비스 ID 오류";
        if (cm.trim().isEmpty()) return "댓글이 없습니다 (앱에서 설정)";
        String body = "key=" + enc(k) + "&action=add&service=" + enc(s)
                + "&link=" + enc(link) + "&comments=" + enc(cm);
        String resp = post(body);
        try {
            JSONObject j = new JSONObject(resp);
            if (j.has("order")) return "주문 완료! 주문번호 " + j.getString("order");
            if (j.has("error")) return "실패: " + j.getString("error");
            return "응답: " + resp;
        } catch (Exception e) {
            return "응답 해석 실패: " + resp;
        }
    }

    public static String balance(Context c) {
        String k = key(c);
        if (k.isEmpty()) return "API 키가 없습니다";
        String resp = post("key=" + enc(k) + "&action=balance");
        try {
            JSONObject j = new JSONObject(resp);
            if (j.has("balance")) return "연결 성공 (잔액 " + j.getString("balance") + " " + j.optString("currency", "") + ")";
            if (j.has("error")) return "실패: " + j.getString("error");
            return "응답: " + resp;
        } catch (Exception e) {
            return "응답 해석 실패: " + resp;
        }
    }
}
