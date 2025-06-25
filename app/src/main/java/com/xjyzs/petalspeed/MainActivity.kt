package com.xjyzs.petalspeed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.xjyzs.petalspeed.ui.theme.PetalSpeedTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class MainViewModel: ViewModel(){
    var token by mutableStateOf("")
    var servers = mutableStateListOf<JsonObject>()
    var id by mutableStateOf("")
    var serverName by mutableStateOf("")
    var bytesRead by mutableLongStateOf(0L)
    var speed by mutableLongStateOf(0L)
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PetalSpeedTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainUI(
                        viewModel(),
                        Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainUI(viewModel: MainViewModel,modifier: Modifier = Modifier) {
    var serversExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        getAccessToken(viewModel)
    }
    LaunchedEffect(viewModel.token) {
        if (viewModel.token.isNotEmpty()) {
            getNearestNode(viewModel)
        }
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Place,null)
            Text(viewModel.serverName)
            IconButton(onClick = {
                serversExpanded = !serversExpanded
            }) { Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "") }
        }
        DropdownMenu(serversExpanded, onDismissRequest = { serversExpanded = false }) {
            for (i in viewModel.servers) {
                DropdownMenuItem({ Text(i.asJsonObject.get("name").asString) }, onClick = {
                    viewModel.id = i.asJsonObject.get("id").asString
                    viewModel.serverName = i.asJsonObject.get("name").asString
                    serversExpanded = false
                })
            }
        }
        Text("花瓣测速", fontSize = 36.sp)
        Text("${viewModel.speed}Mbps")
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
    ) {
        Button(modifier=Modifier.size(200.dp), shape = CircleShape, onClick = {
            speedTest(viewModel)
        }){
            Icon(
                Icons.Default.Radar, null, modifier = Modifier.size(100.dp)
            )
        }
    }
}

fun getAccessToken(viewModel: MainViewModel){
    val client = OkHttpClient()

    val request = Request.Builder()
        .url("https://speedservice.platform.dbankcloud.cn/edge/v1/authorizations/init")
        .addHeader("Authorization", "JTdCJTIydHlwJTIyJTNBJTIySldUJTIyJTJDJTIyYWxnJTIyJTNBJTIySE1BQytTSEEyNTYlMjIlN0Q=.JTdCJTIycGFja2FnZU5hbWUlMjIlM0ElMjJjb20uaHVhd2VpLmdlbmV4Y2xvdWQuc3BlZWR0ZXN0JTIyJTJDJTIyY2VydCUyMiUzQSUyMkNDN0U0RTFGNUI3NUY0NkJENTRGNzQ2NjdFNTg1MDNDQzQ3MDREM0FFRDBFMkU0NEQyMkZBNkVEODZCRDI3RjElMjIlMkMlMjJhcGlLZXklMjIlM0ElMjJyZ2dhdHVUbUpEJTVDJTJGT3RMMDRVa3NxJTVDJTJGZGxHTjFSamFQd05ZeDk4OGJ0UkZZYThsSzR3Uzh5cEozdVN0JTJCaUdLYW4lNUMlMkZ0UjdRdm80S1k4UkUlMkJHWDJTd1R0bHBiYnNZMlZ0MFdEM0NKUUloSllvY1hMNmNkdnVMb2V6c3ZhVjlTQWYxOXhNbVNOd3JqY2s2VkptZktqJTIyJTJDJTIyYWNjZXNzS2V5JTIyJTNBJTIyZXlKaGEzTnJWbVZ5YzJsdmJpSTZNU3dpWVhCd1EyVnlkRVpRSWpvaWVrZzFUMGd4ZERFNVIzWldWRE5TYldac2FGRlFUVkozVkZSeWRFUnBOVVV3YVN0dE4xbGhPVW92UlQwaUxDSmhjSEJRYTJkT1lXMWxJam9pWTI5dExtaDFZWGRsYVM1blpXNWxlR05zYjNWa0xuTndaV1ZrZEdWemRDSXNJbU4wZVNJNklrRnVaSEp2YVdSTFV5SXNJbXRsZVNJNkltTnlia001TTBGMlJsUlRNMjFCWTFadlVVeE5WWEJTY0c1NGEyaHpWM1ZwYjFkV1JuVkRUMEpDVDBVOUlpd2lkR2x0WlhOMFlXMXdJam94TnpVeE5ESXdOekl4T1RZd0xDSjJaWEp6YVc5dUlqb3dmUSUzRCUzRCUyMiUyQyUyMml2JTIyJTNBJTIyUHJuOWFTQUhUc1J1ZSU1QyUyRkN5JTIyJTJDJTIycGxhdGZvcm0lMjIlM0ElMjJhbmRyb2lkJTIyJTJDJTIydmVyc2lvbiUyMiUzQSUyMjQuMS4wLjMwMCUyMiU3RA==.M/8ymdG1cpymhB9TkYIOfmxRUwJw6f8FES7+mOhTsak=")
        .build()
    viewModel.viewModelScope.launch(Dispatchers.IO) {
        val response = client.newCall(request).execute()
        viewModel.token=Gson().fromJson(
            response.body!!.string(),
            JsonElement::class.java
        ).asJsonObject.get("accessToken").asString
    }
}

fun getNearestNode(viewModel: MainViewModel){
    val client = OkHttpClient()

    val mediaType = "application/json".toMediaType()
    val body = "{\"size\":5}".toRequestBody(mediaType)
    val request = Request.Builder()
        .url("https://speedservice.platform.dbankcloud.cn/search/v1/speed-nodes/query-nearest")
        .post(body)
        .addHeader("Authorization", viewModel.token)
        .build()
    viewModel.viewModelScope.launch(Dispatchers.IO) {
        val response = client.newCall(request).execute()
        val servers=Gson().fromJson(
            response.body!!.string(),
            JsonElement::class.java
        ).asJsonObject.get("servers").asJsonArray
        var flag = false
        if (viewModel.servers.isNotEmpty()){
            viewModel.servers.clear()
        }
        for (i in servers){
            viewModel.servers.add(i as JsonObject)
            if (!flag){
                flag=true
                viewModel.id=i.asJsonObject.get("id").asString
                viewModel.serverName = i.asJsonObject.get("name").asString
            }
        }
    }
}


fun speedTest(viewModel: MainViewModel) {
    val client = OkHttpClient()

    val mediaType = "application/json".toMediaType()
    val body = "{\"list\":[\"${viewModel.id}\"]}".toRequestBody(mediaType)
    val request = Request.Builder()
        .url("https://speedservice.platform.dbankcloud.cn/search/v1/speed-nodes/query-by-ids")
        .post(body)
        .addHeader("Authorization", viewModel.token)
        .build()
    viewModel.viewModelScope.launch(Dispatchers.IO) {
        val response = client.newCall(request).execute()
        val testURL = Gson().fromJson(
            response.body!!.string(),
            JsonElement::class.java
        ).asJsonObject.get("servers").asJsonArray[0].asJsonObject.get("httpDlUrl").asString

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        viewModel.bytesRead = 0
        var runnable: Runnable? = null
        runnable = object : Runnable {
            override fun run() {
                viewModel.speed = viewModel.bytesRead*32/1000000
                viewModel.bytesRead = 0
                handler.postDelayed(this, 250)
            }
        }
        handler.post(runnable)

        OkHttpClient().newCall(
            Request.Builder().url(testURL).addHeader("Host", "windspeed-drcn.dbankcdn.com").build()
        ).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                try {
                    val source = response.body?.source()
                    val buffer = okio.Buffer()

                    while (source?.read(buffer, 65536) != -1L) {
                        viewModel.bytesRead += buffer.size
                        buffer.clear()
                    }
                } finally {
                    response.close()
                    handler.removeCallbacks(runnable)
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                handler.removeCallbacks(runnable)
            }
        })
    }
}