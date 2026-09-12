package com.smartcooking.app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@CapacitorPlugin(name = "TemperatureBluetooth", permissions = {
    @Permission(strings = { Manifest.permission.BLUETOOTH_SCAN }, alias = "bluetoothScan"),
    @Permission(strings = { Manifest.permission.BLUETOOTH_CONNECT }, alias = "bluetoothConnect"),
    @Permission(strings = { Manifest.permission.ACCESS_FINE_LOCATION }, alias = "location")
})
public class TemperatureBluetoothPlugin extends Plugin {
    private static final String TAG = "CookXBluetooth";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String PREFS = "cookx_bluetooth";
    private static final String LAST_ADDRESS = "cookx_last_jdy31_address";
    private enum State { IDLE, SCANNING, CONNECTING, CONNECTED, DISCONNECTED, RECONNECTING, ERROR }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, BluetoothDevice> discovered = new ConcurrentHashMap<>();
    private volatile BluetoothAdapter adapter;
    private volatile BluetoothDevice currentDevice;
    private volatile BluetoothSocket socket;
    private volatile InputStream input;
    private volatile OutputStream output;
    private volatile Future<?> connectTask;
    private volatile Future<?> readerTask;
    private volatile State state = State.IDLE;
    private volatile boolean receiverRegistered;
    private volatile boolean userDisconnected;
    private volatile int reconnectAttempt;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) { Log.d(TAG, "ACTION_DISCOVERY_STARTED"); setState(State.SCANNING, null, ""); }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(TAG, "ACTION_DISCOVERY_FINISHED: devices=" + discovered.size());
                if (state == State.SCANNING) setState(State.IDLE, null, "");
                notifyListeners("discoveryFinished", new JSObject());
            } else if (BluetoothDevice.ACTION_FOUND.equals(action) || BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice device = getDevice(intent);
                if (device != null) { Log.d(TAG, "ACTION_FOUND: name=" + safeName(device) + ", address=" + safeAddress(device) + ", bondState=" + safeBondState(device)); publishDevice(device); }
            }
        }
    };

    @Override public void load() {
        BluetoothManager manager = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        mainHandler.post(this::registerReceiverOnce);
    }

    @PluginMethod public void checkBluetoothState(PluginCall call) {
        JSObject result = new JSObject();
        result.put("supported", adapter != null);
        result.put("enabled", adapter != null && safeEnabled());
        result.put("permissionsGranted", hasPermissions());
        result.put("state", stateName());
        call.resolve(result);
    }

    @PluginMethod public void requestBluetoothPermissions(PluginCall call) {
        if (hasPermissions()) { call.resolve(permissionResult()); return; }
        requestAllPermissions(call, "permissionsCallback");
    }

    @PermissionCallback private void permissionsCallback(PluginCall call) {
        if (hasPermissions()) call.resolve(permissionResult());
        else call.reject("Bluetooth permission was denied", "PERMISSION_DENIED");
    }

    @PluginMethod public void startDiscovery(PluginCall call) {
        if (!ensureReady(call)) return;
        mainHandler.post(() -> startDiscoveryOnMainThread(call));
    }

    private void startDiscoveryOnMainThread(PluginCall call) {
        try {
            registerReceiverOnce();
            boolean enabled = adapter != null && adapter.isEnabled();
            boolean discovering = adapter != null && adapter.isDiscovering();
            Log.d(TAG, "startDiscovery: sdk=" + Build.VERSION.SDK_INT
                + ", adapterNull=" + (adapter == null)
                + ", enabled=" + enabled
                + ", discovering=" + discovering
                + ", scanGranted=" + (getPermissionState("bluetoothScan") == PermissionState.GRANTED)
                + ", connectGranted=" + (getPermissionState("bluetoothConnect") == PermissionState.GRANTED)
                + ", fineLocationGranted=" + (getPermissionState("location") == PermissionState.GRANTED)
                + ", receiverRegistered=" + receiverRegistered);
            discovered.clear();
            if (discovering) {
                boolean cancelled = adapter.cancelDiscovery();
                Log.d(TAG, "cancelDiscovery returned=" + cancelled);
            }
            boolean started = adapter.startDiscovery();
            Log.d(TAG, "startDiscovery returned=" + started);
            if (!started) { call.reject("Unable to start Bluetooth discovery", "SCAN_FAILED"); return; }
            setState(State.SCANNING, null, "");
            JSObject result = new JSObject(); result.put("status", "scanning"); call.resolve(result);
        } catch (SecurityException error) {
            Log.e(TAG, "startDiscovery permission failure", error);
            call.reject("Bluetooth scan permission is missing", "PERMISSION_DENIED", error);
        } catch (Exception error) {
            Log.e(TAG, "startDiscovery framework failure", error);
            call.reject("Unable to start Bluetooth discovery", "SCAN_FAILED", error);
        }
    }

    @PluginMethod public void stopDiscovery(PluginCall call) {
        mainHandler.post(() -> { stopDiscoveryInternal(); JSObject result = new JSObject(); result.put("status", "stopped"); call.resolve(result); });
    }

    @PluginMethod public void connect(PluginCall call) {
        if (!ensureReady(call)) return;
        String address = call.getString("address");
        try {
            if (address == null || address.isBlank()) address = prefs().getString(LAST_ADDRESS, null);
            if (address == null || !BluetoothAdapter.checkBluetoothAddress(address)) { call.reject("Select a discovered JDY-31 device", "DEVICE_NOT_FOUND"); return; }
            BluetoothDevice device = discovered.get(address.toUpperCase(Locale.ROOT));
            if (device == null) device = adapter.getRemoteDevice(address);
            userDisconnected = false; reconnectAttempt = 0; currentDevice = device;
            BluetoothDevice selected = device;
            mainHandler.post(() -> {
                stopDiscoveryInternal();
                connectTask = executor.submit(() -> connectDevice(selected, call, false));
            });
        } catch (SecurityException | IllegalArgumentException error) { call.reject("Unable to access the selected device", "DEVICE_NOT_FOUND"); }
    }

    private void connectDevice(BluetoothDevice device, PluginCall call, boolean reconnecting) {
        setState(reconnecting ? State.RECONNECTING : State.CONNECTING, device, "");
        String mode = "insecure";
        try {
            Log.i(TAG, "Insecure RFCOMM attempt " + safeAddress(device));
            openSocket(device, false);
        } catch (IOException insecureError) {
            Log.w(TAG, "Insecure failed; secure fallback", insecureError); closeResources(); mode = "secure";
            try { openSocket(device, true); }
            catch (IOException secureError) { connectionFailed(device, call, reconnecting, secureError); return; }
        } catch (SecurityException error) { fail(call, "PERMISSION_DENIED", "Bluetooth connect permission is missing", error); return; }
        reconnectAttempt = 0;
        prefs().edit().putString(LAST_ADDRESS, safeAddress(device)).apply();
        setState(State.CONNECTED, device, "", "");
        if (call != null) { JSObject result = deviceObject(device); result.put("status", "connected"); result.put("mode", mode); call.resolve(result); }
        readerTask = executor.submit(this::readLoop);
    }

    private void openSocket(BluetoothDevice device, boolean secure) throws IOException {
        BluetoothSocket candidate = secure ? device.createRfcommSocketToServiceRecord(SPP_UUID) : device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
        socket = candidate; candidate.connect(); input = candidate.getInputStream(); output = candidate.getOutputStream();
    }

    private void readLoop() {
        byte[] buffer = new byte[1024]; Log.i(TAG, "Reader started");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                InputStream stream = input; if (stream == null) break;
                int count = stream.read(buffer); if (count < 0) break; if (count == 0) continue;
                byte[] bytes = Arrays.copyOf(buffer, count);
                JSObject raw = new JSObject(); raw.put("hex", toHex(bytes)); raw.put("length", count); raw.put("ascii", safeAscii(bytes)); notifyListeners("dataReceived", raw);
                JSObject legacy = new JSObject(); legacy.put("chunk", new String(bytes, StandardCharsets.US_ASCII)); notifyListeners("temperatureData", legacy);
                Log.d(TAG, "Received " + count + " bytes: " + toHex(bytes));
            }
        } catch (IOException error) { Log.w(TAG, "SPP stream closed", error); }
        finally { if (!userDisconnected) connectionLost(); }
    }

    @PluginMethod public void write(PluginCall call) {
        if (state != State.CONNECTED || socket == null || !socket.isConnected() || output == null) { call.reject("Bluetooth device is not connected", "NOT_CONNECTED"); return; }
        String type = call.getString("type", "text"); String data = call.getString("data");
        if (data == null) { call.reject("Write data is required", "WRITE_FAILED"); return; }
        try {
            byte[] bytes = "hex".equalsIgnoreCase(type) ? parseHex(data) : data.getBytes(StandardCharsets.UTF_8);
            output.write(bytes); output.flush(); Log.d(TAG, "Wrote " + bytes.length + " bytes");
            JSObject result = new JSObject(); result.put("status", "written"); result.put("length", bytes.length); call.resolve(result);
        } catch (IllegalArgumentException error) { call.reject("Invalid hexadecimal data", "WRITE_FAILED"); }
        catch (IOException error) { call.reject("Bluetooth write failed", "WRITE_FAILED", error); }
    }

    @PluginMethod public void disconnect(PluginCall call) {
        userDisconnected = true; reconnectAttempt = 3; cancelTasks(); closeResources(); setState(State.DISCONNECTED, currentDevice, "User disconnected", "USER_DISCONNECTED"); currentDevice = null;
        JSObject result = new JSObject(); result.put("status", "disconnected"); call.resolve(result);
    }

    @PluginMethod public void getConnectionState(PluginCall call) {
        JSObject result = stateObject(state, currentDevice, ""); result.put("connected", state == State.CONNECTED && socket != null && socket.isConnected()); call.resolve(result);
    }

    @PluginMethod public void isConnected(PluginCall call) { getConnectionState(call); }

    private void connectionLost() {
        BluetoothDevice device = currentDevice; closeResources(); setState(State.DISCONNECTED, device, "Connection lost", "CONNECTION_LOST");
        if (device != null && reconnectAttempt < 3) reconnect(device); else setState(State.ERROR, device, "Reconnect limit reached", "RECONNECT_EXHAUSTED");
    }

    private void reconnect(BluetoothDevice device) {
        reconnectAttempt++; long delay = 1L << (reconnectAttempt - 1); setState(State.RECONNECTING, device, "Retry " + reconnectAttempt + " of 3");
        connectTask = executor.submit(() -> { try { Thread.sleep(delay * 1000L); if (!userDisconnected) connectDevice(device, null, true); } catch (InterruptedException error) { Thread.currentThread().interrupt(); } });
    }

    private void connectionFailed(BluetoothDevice device, PluginCall call, boolean reconnecting, Exception error) {
        closeResources();
        if (reconnecting && !userDisconnected && reconnectAttempt < 3) reconnect(device);
        else fail(call, "CONNECT_FAILED", "Unable to connect to JDY-31", error);
    }

    private void publishDevice(BluetoothDevice device) {
        try { String address = device.getAddress(); if (address == null) return; discovered.put(address.toUpperCase(Locale.ROOT), device); JSObject data = deviceObject(device); notifyListeners("deviceFound", data); Log.d(TAG, "Device found " + data); }
        catch (SecurityException error) { Log.w(TAG, "Cannot read discovered device", error); }
    }

    private JSObject deviceObject(BluetoothDevice device) {
        JSObject result = new JSObject(); String name = safeName(device);
        result.put("name", name); result.put("address", safeAddress(device)); result.put("bondState", safeBondState(device)); result.put("isJdy31", name.toUpperCase(Locale.ROOT).contains("JDY-31")); return result;
    }

    private void setState(State next, BluetoothDevice device, String message) { setState(next, device, message, ""); }
    private void setState(State next, BluetoothDevice device, String message, String errorCode) { state = next; notifyListeners("connectionStateChanged", stateObject(next, device, message, errorCode)); Log.i(TAG, "State " + next + ": " + message); }
    private JSObject stateObject(State value, BluetoothDevice device, String message) { return stateObject(value, device, message, ""); }
    private JSObject stateObject(State value, BluetoothDevice device, String message, String errorCode) { JSObject result = deviceObject(device); result.put("state", value.name().toLowerCase(Locale.ROOT)); result.put("message", message); result.put("errorCode", errorCode); return result; }
    private void fail(PluginCall call, String code, String message, Exception error) { setState(State.ERROR, currentDevice, message, code); if (call != null) call.reject(message, code, error); }
    private boolean ensureReady(PluginCall call) { if (adapter == null) { call.reject("Bluetooth is not supported", "BLUETOOTH_UNSUPPORTED"); return false; } if (!hasPermissions()) { call.reject("Bluetooth permission is required", "PERMISSION_DENIED"); return false; } if (!safeEnabled()) { call.reject("Please enable Bluetooth and try again", "BLUETOOTH_DISABLED"); return false; } return true; }
    private boolean hasPermissions() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return getPermissionState("bluetoothScan") == PermissionState.GRANTED && getPermissionState("bluetoothConnect") == PermissionState.GRANTED && getPermissionState("location") == PermissionState.GRANTED; return getPermissionState("location") == PermissionState.GRANTED; }
    private JSObject permissionResult() { JSObject result = new JSObject(); result.put("granted", true); result.put("scanGranted", getPermissionState("bluetoothScan") == PermissionState.GRANTED); result.put("connectGranted", getPermissionState("bluetoothConnect") == PermissionState.GRANTED); result.put("fineLocationGranted", getPermissionState("location") == PermissionState.GRANTED); return result; }
    private boolean safeEnabled() { try { return adapter != null && adapter.isEnabled(); } catch (SecurityException error) { return false; } }
    private void stopDiscoveryInternal() { if (Looper.myLooper() != Looper.getMainLooper()) { mainHandler.post(this::stopDiscoveryInternal); return; } try { if (adapter != null && adapter.isDiscovering()) { boolean cancelled = adapter.cancelDiscovery(); Log.d(TAG, "cancelDiscovery returned=" + cancelled); } } catch (SecurityException error) { Log.w(TAG, "Cannot stop discovery", error); } }
    private void registerReceiverOnce() { if (receiverRegistered) return; IntentFilter filter = new IntentFilter(); filter.addAction(BluetoothDevice.ACTION_FOUND); filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED); filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED); filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED); if (Build.VERSION.SDK_INT >= 33) getContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED); else getContext().registerReceiver(receiver, filter); receiverRegistered = true; Log.d(TAG, "Classic Bluetooth receiver registered on main thread=" + (Looper.myLooper() == Looper.getMainLooper())); }
    private BluetoothDevice getDevice(Intent intent) { if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class); return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE); }
    private String safeName(BluetoothDevice device) { if (device == null) return ""; try { String name = device.getName(); return name == null ? "Unknown device" : name; } catch (SecurityException error) { return "Unknown device"; } }
    private String safeAddress(BluetoothDevice device) { if (device == null) return ""; try { return device.getAddress(); } catch (SecurityException error) { return ""; } }
    private int safeBondState(BluetoothDevice device) { if (device == null) return BluetoothDevice.BOND_NONE; try { return device.getBondState(); } catch (SecurityException error) { return BluetoothDevice.BOND_NONE; } }
    private SharedPreferences prefs() { return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private void cancelTasks() { if (connectTask != null) connectTask.cancel(true); if (readerTask != null) readerTask.cancel(true); connectTask = null; readerTask = null; }
    private void closeResources() { InputStream in = input; input = null; OutputStream out = output; output = null; BluetoothSocket current = socket; socket = null; try { if (in != null) in.close(); } catch (IOException ignored) {} try { if (out != null) out.close(); } catch (IOException ignored) {} try { if (current != null) current.close(); } catch (IOException ignored) {} }
    private static byte[] parseHex(String value) { String clean = value.replaceAll("\\s+", ""); if ((clean.length() & 1) != 0) throw new IllegalArgumentException(); ByteArrayOutputStream out = new ByteArrayOutputStream(); for (int i = 0; i < clean.length(); i += 2) out.write(Integer.parseInt(clean.substring(i, i + 2), 16)); return out.toByteArray(); }
    private static String toHex(byte[] bytes) { StringBuilder result = new StringBuilder(); for (byte value : bytes) { if (result.length() > 0) result.append(' '); result.append(String.format(Locale.US, "%02X", value & 0xff)); } return result.toString(); }
    private static String safeAscii(byte[] bytes) { StringBuilder result = new StringBuilder(); for (byte value : bytes) { int item = value & 0xff; result.append(item >= 32 && item <= 126 ? (char) item : '.'); } return result.toString(); }
    private String stateName() { return state.name().toLowerCase(Locale.ROOT); }

    @Override protected void handleOnDestroy() { userDisconnected = true; stopDiscoveryInternal(); cancelTasks(); closeResources(); if (receiverRegistered) { try { getContext().unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) {} receiverRegistered = false; } executor.shutdownNow(); super.handleOnDestroy(); }
}
