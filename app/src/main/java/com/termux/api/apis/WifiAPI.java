package com.termux.api.apis;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.format.Formatter;
import android.text.TextUtils;
import android.util.JsonWriter;
import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;
import java.util.List;

public class WifiAPI {

  private static final String LOG_TAG = "WifiAPI";

  public static void onReceiveWifiConnectionInfo(TermuxApiReceiver apiReceiver, final Context context,
      final Intent intent) {
    Logger.logDebug(LOG_TAG, "onReceiveWifiConnectionInfo");

    ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
      @SuppressLint("HardwareIds")
      @Override
      public void writeJson(JsonWriter out) throws Exception {
        WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = manager.getConnectionInfo();
        out.beginObject();
        if (info == null) {
          out.name("API_ERROR").value("No current connection");
        } else {
          out.name("bssid").value(info.getBSSID());
          out.name("frequency_mhz").value(info.getFrequency());
          // noinspection deprecation - formatIpAddress is deprecated, but we only have a
          // ipv4 address here:
          out.name("ip").value(Formatter.formatIpAddress(info.getIpAddress()));
          out.name("link_speed_mbps").value(info.getLinkSpeed());
          out.name("mac_address").value(info.getMacAddress());
          out.name("network_id").value(info.getNetworkId());
          out.name("rssi").value(info.getRssi());
          out.name("ssid").value(info.getSSID().replaceAll("\"", ""));
          out.name("ssid_hidden").value(info.getHiddenSSID());
          out.name("supplicant_state").value(info.getSupplicantState().toString());
        }
        out.endObject();
      }
    });
  }

  static boolean isLocationEnabled(Context context) {
    LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    return lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
  }

  public static void onReceiveWifiScanInfo(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
    Logger.logDebug(LOG_TAG, "onReceiveWifiScanInfo");

    ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
      @Override
      public void writeJson(JsonWriter out) throws Exception {
        WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        List<ScanResult> scans = manager.getScanResults();
        if (scans == null) {
          out.beginObject().name("API_ERROR").value("Failed getting scan results").endObject();
        } else if (scans.isEmpty() && !isLocationEnabled(context)) {
          // https://issuetracker.google.com/issues/37060483:
          // "WifiManager#getScanResults() returns an empty array list if GPS is turned
          // off"
          String errorMessage = "Location needs to be enabled on the device";
          out.beginObject().name("API_ERROR").value(errorMessage).endObject();
        } else {
          out.beginArray();
          for (ScanResult scan : scans) {
            out.beginObject();
            out.name("bssid").value(scan.BSSID);
            out.name("frequency_mhz").value(scan.frequency);
            out.name("rssi").value(scan.level);
            out.name("ssid").value(scan.SSID);
            out.name("timestamp").value(scan.timestamp);

            int channelWidth = scan.channelWidth;
            String channelWidthMhz = "???";
            switch (channelWidth) {
              case ScanResult.CHANNEL_WIDTH_20MHZ:
                channelWidthMhz = "20";
                break;
              case ScanResult.CHANNEL_WIDTH_40MHZ:
                channelWidthMhz = "40";
                break;
              case ScanResult.CHANNEL_WIDTH_80MHZ:
                channelWidthMhz = "80";
                break;
              case ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ:
                channelWidthMhz = "80+80";
                break;
              case ScanResult.CHANNEL_WIDTH_160MHZ:
                channelWidthMhz = "160";
                break;
            }
            out.name("channel_bandwidth_mhz").value(channelWidthMhz);
            if (channelWidth != ScanResult.CHANNEL_WIDTH_20MHZ) {
              // centerFreq0 says "Not used if the AP bandwidth is 20 MHz".
              out.name("center_frequency_mhz").value(scan.centerFreq0);
            }
            if (!TextUtils.isEmpty(scan.capabilities)) {
              out.name("capabilities").value(scan.capabilities);
            }
            if (!TextUtils.isEmpty(scan.operatorFriendlyName)) {
              out.name("operator_name").value(scan.operatorFriendlyName.toString());
            }
            if (!TextUtils.isEmpty(scan.venueName)) {
              out.name("venue_name").value(scan.venueName.toString());
            }
            out.endObject();
          }
          out.endArray();
        }
      }
    });
  }

  public static void onReceiveWifiEnable(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
    Logger.logDebug(LOG_TAG, "onReceiveWifiEnable");

    ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
      @Override
      public void writeJson(JsonWriter out) {
        WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        boolean state = intent.getBooleanExtra("enabled", false);
        manager.setWifiEnabled(state);
      }
    });
  }

  public static void onReceiveWifiConnect(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
    Logger.logDebug(LOG_TAG, "onReceiveWifiConnect");

    ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
      @Override
      public void writeJson(JsonWriter out) throws Exception {
        WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (!manager.isWifiEnabled()) {
          out.beginObject();
          out.name("API_ERROR").value("Wi-Fi is disabled. Enable it first.");
          out.endObject();
          return;
        }

        String pass = "";
        String security = intent.hasExtra("security") ? intent.getStringExtra("security").toLowerCase() : "wpa";
        String ssid = intent.getStringExtra("ssid");
        if (!security.equals("open")) {
          pass = intent.getStringExtra("pass");
        }
        if (ssid == null || ssid.isEmpty()) {
          out.beginObject();
          out.name("API_ERROR").value("SSID is required");
          out.endObject();
          return;
        }

        // Поддерживаем: open, wpa, wpa2, wpa3 (упрощённо)
        int networkId = -1;

        // Удаляем старую конфигурацию, если есть
        removeExistingNetwork(manager, ssid);

        // В зависимости от типа безопасности — создаём конфигурацию
        if (security.contains("wpa")) {
          if (security.contains("wpa3") && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            networkId = addWPA3Network(manager, ssid, pass);
          } else {
            networkId = addWPA2Network(manager, ssid, pass);
          }
        } else if (security.equals("open")) {
          networkId = addOpenNetwork(manager, ssid);
        } else {
          out.beginObject();
          out.name("API_ERROR").value("Unsupported security type: " + security);
          out.endObject();
          return;
        }

        if (networkId == -1) {
          out.beginObject();
          out.name("API_ERROR").value("Failed to add network");
          out.endObject();
          return;
        }

        boolean success = manager.enableNetwork(networkId, true);
        out.beginObject();
        if (success) {
          out.name("result").value("Connected to " + ssid);
        } else {
          out.name("API_ERROR").value("Failed to enable network");
        }
        out.endObject();
      }
    });
  }

  // Удалить существующую сеть с таким SSID
  private static void removeExistingNetwork(WifiManager manager, String ssid) {
    List<WifiConfiguration> configs = manager.getConfiguredNetworks();
    if (configs != null) {
      for (WifiConfiguration config : configs) {
        if (config.SSID != null && config.SSID.equals("\"" + ssid + "\"")) {
          manager.removeNetwork(config.networkId);
          manager.saveConfiguration();
        }
      }
    }
  }

  // Подключение к открытой сети
  private static int addOpenNetwork(WifiManager manager, String ssid) {
    WifiConfiguration config = new WifiConfiguration();
    config.SSID = "\"" + ssid + "\"";
    config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
    return manager.addNetwork(config);
  }

  // Подключение к WPA/WPA2
  private static int addWPA2Network(WifiManager manager, String ssid, String pass) {
    WifiConfiguration config = new WifiConfiguration();
    config.SSID = "\"" + ssid + "\"";
    config.preSharedKey = "\"" + pass + "\"";
    config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
    config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
    config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
    config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
    config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
    config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
    config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
    return manager.addNetwork(config);
  }

  private static int addWPA3Network(WifiManager manager, String ssid, String pass) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
      return -1; // WPA3 не поддерживается
    }

    try {
      WifiConfiguration config = new WifiConfiguration();
      config.SSID = "\"" + ssid + "\"";
      config.preSharedKey = "\"" + pass + "\"";

      // Получаем поле через reflection, так как requirePmf нет в старых SDK
      java.lang.reflect.Field requirePmfField = WifiConfiguration.class.getDeclaredField("requirePmf");
      requirePmfField.setAccessible(true);
      requirePmfField.set(config, true);

      // Устанавливаем KeyMgmt SAE (WPA3-Personal)
      config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.SAE);

      return manager.addNetwork(config);
    } catch (Exception e) {
      return -1;
    }
  }

}
