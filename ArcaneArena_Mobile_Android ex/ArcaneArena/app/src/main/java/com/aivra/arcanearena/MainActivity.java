package com.aivra.arcanearena;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Arcane Arena — WebView game + Bluetooth Classic (SPP) bridge to the HC-05.
 * The game (assets/index.html) calls window.AndroidBT.listPaired() / connect() / disconnect(),
 * and receives data through window.onBTData(text) and status through window.onBTStatus(state, info).
 */
public class MainActivity extends Activity {

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int REQ_BT = 42;

    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private BluetoothAdapter adapter;
    private volatile BluetoothSocket socket;
    private volatile boolean running = false;
    private Thread readThread;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bm != null ? bm.getAdapter() : null;

        web = new WebView(this);
        web.setBackgroundColor(0xFF0B0D17);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new BtBridge(), "AndroidBT");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");

        hideSystemUi();
        askBtPermission();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    /* ---------------- permissions ---------------- */
    private boolean hasBtPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void askBtPermission() {
        if (Build.VERSION.SDK_INT >= 31 && !hasBtPermission()) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
        }
    }

    /* ---------------- JS helpers ---------------- */
    private void js(final String code) {
        ui.post(() -> { if (web != null) web.evaluateJavascript(code, null); });
    }

    private void status(String state, String info) {
        js("window.onBTStatus && window.onBTStatus(" + JSONObject.quote(state) + "," + JSONObject.quote(info == null ? "" : info) + ")");
    }

    /* ---------------- Bluetooth ---------------- */
    @SuppressLint("MissingPermission")
    private void doConnect(final String address, final String name) {
        closeSocket();
        status("connecting", name);
        new Thread(() -> {
            try {
                if (adapter == null) throw new IOException("This phone has no Bluetooth");
                if (!adapter.isEnabled()) throw new IOException("Turn Bluetooth ON first");
                BluetoothDevice dev = adapter.getRemoteDevice(address);
                try { adapter.cancelDiscovery(); } catch (Exception ignored) { }

                BluetoothSocket sock;
                try {
                    sock = dev.createRfcommSocketToServiceRecord(SPP_UUID);
                    sock.connect();
                } catch (IOException first) {
                    // Classic HC-05 fallback: open RFCOMM channel 1 directly
                    Method m = dev.getClass().getMethod("createRfcommSocket", int.class);
                    sock = (BluetoothSocket) m.invoke(dev, 1);
                    sock.connect();
                }
                socket = sock;
                status("connected", name);
                startReader(sock);
            } catch (Exception e) {
                closeSocket();
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                status("error", "could not connect (" + msg + "). Is the Arduino powered and the HC-05 LED blinking?");
            }
        }).start();
    }

    private void startReader(final BluetoothSocket sock) {
        running = true;
        readThread = new Thread(() -> {
            byte[] buf = new byte[256];
            try {
                InputStream in = sock.getInputStream();
                while (running) {
                    int n = in.read(buf);
                    if (n < 0) break;
                    if (n > 0) {
                        String txt = new String(buf, 0, n, StandardCharsets.US_ASCII);
                        js("window.onBTData && window.onBTData(" + JSONObject.quote(txt) + ")");
                    }
                }
            } catch (IOException ignored) {
            }
            if (running && socket == sock) { // lost connection (not a user disconnect)
                running = false;
                closeSocket();
                status("disconnected", "");
            }
        });
        readThread.start();
    }

    private void closeSocket() {
        running = false;
        BluetoothSocket s = socket;
        socket = null;
        if (s != null) {
            try { s.close(); } catch (IOException ignored) { }
        }
    }

    /** Methods callable from JavaScript as window.AndroidBT.xxx() */
    private class BtBridge {
        @JavascriptInterface
        @SuppressLint("MissingPermission")
        public String listPaired() {
            try {
                if (adapter == null) return "{\"error\":\"This phone has no Bluetooth.\"}";
                if (!hasBtPermission()) {
                    ui.post(MainActivity.this::askBtPermission);
                    return "{\"error\":\"Please allow the Bluetooth permission, then tap Refresh.\"}";
                }
                if (!adapter.isEnabled()) return "{\"error\":\"Bluetooth is OFF. Turn it on, then tap Refresh.\"}";
                Set<BluetoothDevice> bonded = adapter.getBondedDevices();
                JSONArray arr = new JSONArray();
                for (BluetoothDevice d : bonded) {
                    JSONObject o = new JSONObject();
                    String n = d.getName();
                    o.put("name", n == null ? "(unknown)" : n);
                    o.put("address", d.getAddress());
                    arr.put(o);
                }
                return arr.toString();
            } catch (Exception e) {
                return "{\"error\":" + JSONObject.quote("Bluetooth error: " + e.getMessage()) + "}";
            }
        }

        @JavascriptInterface
        public void connect(String address, String name) {
            if (!hasBtPermission()) {
                ui.post(MainActivity.this::askBtPermission);
                status("error", "Bluetooth permission needed");
                return;
            }
            doConnect(address, name);
        }

        @JavascriptInterface
        public void disconnect() {
            closeSocket();
        }

        @JavascriptInterface
        public boolean isConnected() {
            return socket != null && running;
        }
    }

    @Override
    protected void onDestroy() {
        closeSocket();
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
