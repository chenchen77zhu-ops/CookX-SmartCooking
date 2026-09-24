package com.smartcooking.app;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import com.getcapacitor.*;
import com.getcapacitor.annotation.*;
import java.util.ArrayList;
import java.util.Locale;

@CapacitorPlugin(name="CookingVoice", permissions={@Permission(strings={Manifest.permission.RECORD_AUDIO},alias="microphone")})
public class CookingVoicePlugin extends Plugin {
    private final Handler main = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private PluginCall pending;
    private TextToSpeech tts;
    private boolean chineseReady=false;
    private boolean foreground=true;
    private String speechId;
    private final Runnable timeout=()->cancelRecognition("识别超时，请重试");

    @Override public void load() {
        main.post(()->{
            tts=new TextToSpeech(getContext(),status->{
                main.post(()->{
                    if(tts==null)return;
                    chineseReady=status==TextToSpeech.SUCCESS && tts.setLanguage(Locale.SIMPLIFIED_CHINESE)>=0;
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                        public void onStart(String id){speechEvent(id,"playing");}
                        public void onDone(String id){speechEvent(id,"idle");}
                        public void onError(String id){speechEvent(id,"error");}
                    });
                });
            });
        });
    }
    private void speechEvent(String id,String state){JSObject data=new JSObject();data.put("id",id);data.put("state",state);notifyListeners("speechState",data);}
    @PluginMethod public void capabilities(PluginCall call){main.post(()->{
        JSObject data=new JSObject();data.put("schemaVersion",1);data.put("recognitionAvailable",SpeechRecognizer.isRecognitionAvailable(getContext()));data.put("microphone",getPermissionState("microphone").toString());data.put("chineseSpeechAvailable",chineseReady);call.resolve(data);
    });}
    @PluginMethod public void start(PluginCall call){
        if(getPermissionState("microphone")!=PermissionState.GRANTED){requestPermissionForAlias("microphone",call,"microphoneResult");return;}
        main.post(()->startOnMain(call));
    }
    @PermissionCallback private void microphoneResult(PluginCall call){
        if(getPermissionState("microphone")!=PermissionState.GRANTED){call.reject("麦克风权限未授予，请使用文字或按钮");return;}
        main.post(()->startOnMain(call));
    }
    private void startOnMain(PluginCall call){
        cancelRecognition("已开始新的识别");stopTts();
        if(!foreground){call.reject("请返回前台再开始识别");return;}
        if(!SpeechRecognizer.isRecognitionAvailable(getContext())){call.reject("系统识别服务不可用，请使用文字或按钮");return;}
        pending=call;
        try{
            recognizer=SpeechRecognizer.createSpeechRecognizer(getContext());
            recognizer.setRecognitionListener(new RecognitionListener(){
                public void onReadyForSpeech(Bundle b){} public void onBeginningOfSpeech(){} public void onRmsChanged(float r){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){} public void onPartialResults(Bundle b){} public void onEvent(int t,Bundle b){}
                public void onError(int error){if(pending==call)cancelRecognition("系统语音识别失败（"+error+"），请重试或输入指令");}
                public void onResults(Bundle results){
                    if(pending!=call)return;
                    ArrayList<String> texts=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(texts==null||texts.isEmpty()){cancelRecognition("未识别到指令");return;}
                    float[] scores=results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                    JSObject data=new JSObject();data.put("text",texts.get(0));
                    if(scores!=null && scores.length>0 && Float.isFinite(scores[0]) && scores[0]>=0 && scores[0]<=1)data.put("confidence",scores[0]);
                    pending=null;disposeRecognizer();call.resolve(data);
                }
            });
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN");intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
            recognizer.startListening(intent);main.postDelayed(timeout,20000);
        }catch(Exception e){cancelRecognition("无法启动系统识别，请使用文字或按钮");}
    }
    private void disposeRecognizer(){main.removeCallbacks(timeout);if(recognizer!=null){recognizer.cancel();recognizer.destroy();recognizer=null;}}
    private void cancelRecognition(String message){PluginCall previous=pending;pending=null;disposeRecognizer();if(previous!=null)previous.reject(message);}
    @PluginMethod public void stop(PluginCall call){main.post(()->{cancelRecognition("语音识别已取消");call.resolve();});}
    private void stopTts(){if(tts!=null)tts.stop();if(speechId!=null){speechEvent(speechId,"idle");speechId=null;}}
    @PluginMethod public void speak(PluginCall call){main.post(()->{
        cancelRecognition("开始播报");stopTts();
        if(!foreground || !chineseReady || tts==null){call.reject("没有可用中文系统语音，可手动重试在线播报");return;}
        String text=call.getString("text","");speechId=call.getString("id","");
        if(text.isEmpty()||tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,speechId)==TextToSpeech.ERROR){stopTts();call.reject("系统播报失败，可手动重试在线播报");return;}call.resolve();
    });}
    @PluginMethod public void stopSpeaking(PluginCall call){main.post(()->{stopTts();call.resolve();});}
    @Override protected void handleOnPause(){foreground=false;main.post(()->{cancelRecognition("已离开前台");stopTts();});}
    @Override protected void handleOnResume(){foreground=true;}
    @Override protected void handleOnDestroy(){main.post(()->{cancelRecognition("页面已关闭");stopTts();if(tts!=null){tts.shutdown();tts=null;}chineseReady=false;});}
}
