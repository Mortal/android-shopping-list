package dk.strova.mama

import android.content.Context
import com.android.volley.*
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.JsonRequest
import com.android.volley.toolbox.Volley
import org.json.JSONException
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.nio.charset.Charset

class RemoteData(val context: Context, val endpoint: String) {
    companion object {
        @Volatile
        private var requestQueue: RequestQueue? = null

        fun getQueue(context: Context): RequestQueue {
            requestQueue = requestQueue ?: synchronized(this) {
                requestQueue ?: Volley.newRequestQueue(context.applicationContext)
            }
            return requestQueue!!
        }
    }

    val requestQueue: RequestQueue
    get() = getQueue(context)

    sealed class Result {
        data class Success(val items: List<String>): Result()

        data class Error(val error: VolleyError): Result()
    }

    fun getAll(cb: (Result) -> Unit) {
        request(Request.Method.GET, null, cb)
    }

    fun insert(item: String, cb: (Result) -> Unit) {
        request(Request.Method.POST, "form=shopping_list&insert=" + URLEncoder.encode(item, "UTF-8"), cb)
    }

    fun delete(item: String, cb: (Result) -> Unit) {
        request(Request.Method.POST, "form=shopping_list&delete=" + URLEncoder.encode(item, "UTF-8"), cb)
    }

    private fun request(method: Int, data: String?, cb: (Result) -> Unit) {
        requestQueue.add(object: JsonRequest<JSONObject>(method, endpoint, data,
                {
                    val jsonNames = it.getJSONArray("shopping")
                    val result = ArrayList<String>(jsonNames.length())
                    for (i in 0 until jsonNames.length()) {
                        result.add(jsonNames.getString(i))
                    }
                    cb(Result.Success(result))
                },
                { cb(Result.Error(it)) }) {
            override fun parseNetworkResponse(response: NetworkResponse): Response<JSONObject> {
                try {
                    val jsonString = String(response.data,
                            Charset.forName(HttpHeaderParser.parseCharset(response.headers, JsonRequest.PROTOCOL_CHARSET)))
                    return Response.success(JSONObject(jsonString),
                            HttpHeaderParser.parseCacheHeaders(response))
                } catch (e: UnsupportedEncodingException) {
                    return Response.error(ParseError(e))
                } catch (je: JSONException) {
                    return Response.error(ParseError(je))
                }
            }

            override fun getBodyContentType(): String =
                "application/x-www-form-urlencoded; charset=" + PROTOCOL_CHARSET
        })
    }
}
