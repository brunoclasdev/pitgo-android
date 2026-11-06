package com.trustarx.pitgo.features.registrationverification.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.trustarx.pitgo.R
import kotlinx.serialization.json.*
import java.util.UUID

@Composable
fun RegistrationVehiclesForm(data:JsonObject,enabled:Boolean,onSave:(JsonObject)->Unit) {
 var plate by remember{mutableStateOf("")}; var make by remember{mutableStateOf("")}
 var model by remember{mutableStateOf("")};var color by remember{mutableStateOf("")}
 Text(stringResource(R.string.registration_vehicle_help))
 data["vehicles"]?.jsonArray.orEmpty().forEach { item ->
  val vehicle=item.jsonObject
  RegistrationVehicleEditor(vehicle,enabled,onSave={changed->
   val vehicles=data["vehicles"]?.jsonArray.orEmpty().map {if(it.jsonObject["id"]==vehicle["id"])changed else it}
   onSave(JsonObject(data+("vehicles" to JsonArray(vehicles))))
  },onRemove={onSave(JsonObject(data+("vehicles" to JsonArray(data["vehicles"]?.jsonArray.orEmpty().filter {it.jsonObject["id"]!=vehicle["id"]}))))})
 }
 listOf(Triple(R.string.registration_plate,plate,{s:String->plate=s}),Triple(R.string.registration_make,make,{s:String->make=s}),Triple(R.string.registration_model,model,{s:String->model=s}),Triple(R.string.registration_color,color,{s:String->color=s})).forEach {(label,value,set)->
  OutlinedTextField(value,set,label={Text(stringResource(label))},enabled=enabled,singleLine=true,modifier=Modifier.fillMaxWidth())
 }
 OutlinedButton(enabled=enabled&&listOf(plate,make,model,color).all{it.isNotBlank()},onClick={
  val item=buildJsonObject{put("id",UUID.randomUUID().toString());put("plate",plate.trim().uppercase().replace(Regex("[^A-Z0-9]"),""));put("make",make.trim());put("model",model.trim());put("color",color.trim())}
  onSave(JsonObject(data+ ("vehicles" to JsonArray(data["vehicles"]?.jsonArray.orEmpty()+item))))
  plate="";make="";model="";color=""
 }){Text(stringResource(R.string.registration_add_vehicle))}
}

@Composable
private fun RegistrationVehicleEditor(vehicle:JsonObject,enabled:Boolean,onSave:(JsonObject)->Unit,onRemove:()->Unit) {
 var values by remember(vehicle) {mutableStateOf(listOf("plate","make","model","color").map{vehicle[it]?.jsonPrimitive?.content.orEmpty()})}
 val keys=listOf("plate","make","model","color")
 val labels=listOf(R.string.registration_plate,R.string.registration_make,R.string.registration_model,R.string.registration_color)
 labels.forEachIndexed{index,label->OutlinedTextField(values[index],{value->values=values.toMutableList().apply{set(index,value)}},label={Text(stringResource(label))},enabled=enabled,singleLine=true,modifier=Modifier.fillMaxWidth())}
 OutlinedButton(enabled=enabled,onClick={onSave(buildJsonObject {put("id",vehicle.getValue("id"));keys.forEachIndexed{index,key->put(key,if(index==0)values[index].trim().uppercase().replace(Regex("[^A-Z0-9]"),"") else values[index].trim())}})}) {Text(stringResource(R.string.registration_save_vehicle))}
 TextButton(enabled=enabled,onClick=onRemove){Text(stringResource(R.string.registration_remove_vehicle))}
}
