package com.smartcooking.app;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** 网页在 App 退到后台时调用 show，回到前台时调用 hide。 */
@CapacitorPlugin(name = "LiveCooking")
public class LiveCookingPlugin extends Plugin {
    @PluginMethod
    public void show(PluginCall call) {
        LiveCookingNotifier.Payload payload = new LiveCookingNotifier.Payload();
        JSObject data = call.getData();
        payload.connected = data.optBoolean("connected", false);
        payload.temperature = data.isNull("temperature") || !data.has("temperature") ? Double.NaN : data.optDouble("temperature", Double.NaN);
        payload.maxTemperature = data.optInt("maxTemperature", 250);
        payload.targetLow = data.optInt("targetLow", 0);
        payload.targetHigh = data.optInt("targetHigh", 0);
        payload.status = data.optString("status", "");
        payload.dish = data.optString("dish", "");
        payload.step = data.optString("step", "");
        payload.adviceTitle = data.optString("adviceTitle", "下一步建议");
        payload.adviceText = data.optString("adviceText", "");
        payload.timerEndsAt = data.optLong("timerEndsAt", 0);
        payload.alertLevel = LiveCookingNotifier.alertFor(payload);
        LiveCookingNotifier.show(getContext(), payload);
        call.resolve();
    }

    @PluginMethod
    public void hide(PluginCall call) {
        LiveCookingNotifier.hide(getContext());
        call.resolve();
    }
}
