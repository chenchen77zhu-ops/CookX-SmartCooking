package com.smartcooking.app.offline

import android.content.Context
import android.graphics.BitmapFactory
import com.smartcooking.app.R
import com.smartcooking.app.core.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.File
import java.util.UUID

/** Terminal interceptor: never calls chain.proceed, including unknown URLs and media. */
class LocalRuntime(private val context: Context, store:KeyValueStore, sessions:SessionManager):Interceptor {
    val engine=LocalEngine(AppJson.parseToJsonElement(context.assets.open("offline/recipes.json").bufferedReader().use{it.readText()}).asArray()!!.mapNotNull{it.asObject()},store.get("cookx:local-database:v1"),{value->check(store.putSync("cookx:local-database:v1",value)){"本机存储失败"}}, storageRules=AppJson.parseToJsonElement(context.assets.open("offline/storage-rules.json").bufferedReader().use{it.readText()}).asObject()!!)
    init {
        if(!store.contains("cookx:local-initialized:v1")) {
            sessions.save(engine.initialSession())
            check(store.putSync("cookx:local-initialized:v1","1"))
        }
    }
    override fun intercept(chain:Interceptor.Chain):Response {
        val r=chain.request();val p=r.url.encodedPath
        fun reply(code:Int,bytes:ByteArray,mime:String="application/json")=Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message(if(code<400)"OK" else "Error").body(bytes.toResponseBody(mime.toMediaType())).build()
        fun json(code:Int,value:JsonElement)=reply(code,value.toString().toByteArray())
        val headers=r.headers.names().associateWith{r.header(it).orEmpty()}
        val auth=engine.request("GET","/api/auth/session",headers=headers)
        if(p.startsWith("/api/v3/community/media/") || p.startsWith("/api/v3/profile/media/")) {
            if(auth.code!=200)return json(auth.code,auth.body)
            val name=p.substringAfterLast('/');if(!name.matches(Regex("[a-zA-Z0-9-]+")))return json(404,jsonOf("detail" to "图片不存在"))
            val bytes=when(name){"dish-0","dish-1","dish-2"->context.resources.openRawResource(when(name){"dish-1"->R.drawable.sense_pan;"dish-2"->R.drawable.today_dish;else->R.drawable.live_card_pan}).use{it.readBytes()};else->File(context.filesDir,"local-media/$name").takeIf{it.isFile}?.readBytes()}
            return if(bytes!=null)reply(200,bytes,"image/webp")else json(404,jsonOf("detail" to "图片不存在"))
        }
        if(r.body is MultipartBody && p in listOf("/api/upload-avatar","/api/v3/community/media")) {
            if(auth.code!=200)return json(auth.code,auth.body)
            val part=(r.body as MultipartBody).parts.firstOrNull()?:return json(422,jsonOf("detail" to "请选择图片"))
            val buffer=Buffer();part.body.writeTo(buffer);val bytes=buffer.readByteArray()
            val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,opts)
            if(bytes.size>10*1024*1024||opts.outWidth<=0||opts.outHeight<=0)return json(422,jsonOf("detail" to "请选择有效图片，大小不超过 10 MB"))
            val id=UUID.randomUUID().toString();val dir=File(context.filesDir,"local-media").apply{mkdirs()};File(dir,id).writeBytes(bytes)
            return json(200,jsonOf("status" to "success","id" to id,"url" to "/api/v3/profile/media/$id","avatar_url" to "/api/v3/profile/media/$id"))
        }
        val body=if(r.body==null || r.body is MultipartBody)EmptyObject else runCatching{val buffer=Buffer();r.body!!.writeTo(buffer);AppJson.parseToJsonElement(buffer.readUtf8())}.getOrDefault(EmptyObject)
        val res=engine.request(r.method,p,body,r.url.queryParameterNames.associateWith{r.url.queryParameter(it).orEmpty()},headers)
        return json(res.code,res.body)
    }
}
