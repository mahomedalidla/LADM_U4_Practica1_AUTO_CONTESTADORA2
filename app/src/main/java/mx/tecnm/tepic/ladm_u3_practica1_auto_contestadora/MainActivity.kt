package mx.tecnm.tepic.ladm_u3_practica1_auto_contestadora

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.preference.PreferenceManager
import android.provider.CallLog
import android.telephony.SmsManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
    var baseRemota = FirebaseFirestore.getInstance()
    var listaTelefonos = ArrayList<String>()
    val siLecturaLlamadas = 1
    val siEnviarMensajes = 2
    var listaMensajesMandados = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //-----------------------------Permisos-----------------------------//
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.READ_CALL_LOG), siLecturaLlamadas)
        }

        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.SEND_SMS)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.SEND_SMS),siEnviarMensajes)
        }

        btnAgregar.setOnClickListener {
            insertarContactos()
        }

        btnActivo.setOnClickListener {
            var estado = true

            if (estado == true) {
                activoDesactivo(estado)
                !estado
            }

            btnActivo.setEnabled(false)
            btnAgregar.setEnabled(true)
        }

        //-----------------Mensaje de uso de aplicacion-----------------//
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("ATENCION")
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("firstTime", false)) {
            builder.setMessage("Es necesario Activar la aplicacion para  su funcionamiento.\n Es importnate solo agregar los numeros sin espacios, guiones o simbolos.\n Se deben de estar registrados por lo menos un contactos DESEADO y uno NO DESEADO")
            builder.setPositiveButton("OK"){ d,w->
                d.dismiss()
            }
            builder.show()
            // mark first time has ran.
            val editor = prefs.edit()
            editor.putBoolean("firstTime", true)
            editor.commit()
        }

    }

    private fun activoDesactivo(status : Boolean) {

        if (status == true) {

            var timer = object : CountDownTimer(20000, 5000) {
                override fun onTick(millisUntilFinished: Long) {
                    cargarListaLlamadas()
                    alerta("Buscando llamadas perdidas")
                }

                override fun onFinish() {
                    enviarSMS()
                    start()
                }
            }.start()
        }



    }

    private fun insertarContactos() {
        var tipo = ""

        if (rbtnAgradables.isChecked){
            tipo = rbtnAgradables.text.toString()
        }else {
            tipo = rbtnDesagradables.text.toString()
        }

        var datosInsertar = hashMapOf(
            "nombre" to txtNombre.text.toString(),
            "telefono" to txtTelefono.text.toString(),
            "tipo" to tipo
        )

        baseRemota.collection("contactos")
            .add(datosInsertar)
            .addOnSuccessListener {
                mensaje("Se inserto correctamente")

                txtNombre.setText("")
                txtTelefono.setText("")
            }
            .addOnFailureListener {
                mensaje("Fallo! no se pudo insertar")
            }
    }

    private fun enviarSMS() {
        //Contenido de mensajes
        var msjDeseado = "Perdon por no contestar"
        var msjNoDeseado = "No me llames más"

        var tipo = ""
        if (listaTelefonos.isNotEmpty()){
            var telefono = ""
            listaTelefonos.forEach {
                baseRemota.collection("contactos").addSnapshotListener { querySnapshot, error ->
                    if (error != null) {
                        mensaje(error.message!!)
                        return@addSnapshotListener
                    }
                    for (document in querySnapshot!!) {
                        tipo = "${document.getString("tipo")}"
                        telefono = document.getString("telefono").toString()

                        if (listaMensajesMandados.contains(telefono)){

                        }else{
                            //----------------comparacion de llamadas----------//
                            if (tipo.equals("AGRADABLES")) {
                                if (it.equals(document.getString("telefono"))) {
                                    SmsManager.getDefault().sendTextMessage(telefono,null, msjDeseado,null,null)
                                    listaMensajesMandados.add(telefono)
                                }
                            } else {
                                if (it.equals(document.getString("telefono"))) {
                                    SmsManager.getDefault().sendTextMessage(telefono,null, msjNoDeseado,null,null)
                                    listaMensajesMandados.add(telefono)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    //El super permiso
    @SuppressLint("SimpleDateFormat")
    private fun cargarListaLlamadas() {
        var llamadas = ArrayList<String>()
        val seleccion = CallLog.Calls.TYPE + "=" + CallLog.Calls.MISSED_TYPE
        var cursor = contentResolver.query(
            Uri.parse("content://call_log/calls"),
            null, seleccion, null, null
        )
        listaTelefonos.clear()
        var registro = ""
        while (cursor!!.moveToNext()){
            var nombre = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
            var telefono = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                //telefono = telefono.replace(" ".toRegex(), "")
            var fecha = cursor.getString(cursor.getColumnIndex(CallLog.Calls.DATE))

            val seconds: Long = fecha.toLong()
            val formatter = SimpleDateFormat("DD-MM-YY HH:mm")
            val dateString: String = formatter.format(Date(seconds))

            registro = "NOMBRE: ${cursor.getString(nombre)} \nNUMERO: ${cursor.getString(telefono)} \nFECHA: ${dateString}"
            llamadas.add(registro)
            listaTelefonos.add(cursor.getString(telefono))
        }
        listallamadas.adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, llamadas
        )
        cursor.close()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == siLecturaLlamadas){ cargarListaLlamadas() }
    }

    private fun mensaje(s: String) {
        AlertDialog.Builder(this).setTitle("ATENCION")
            .setMessage(s)
            .setPositiveButton("OK"){ d,i-> }
            .show()
    }

    private fun alerta(s: String) {
        Toast.makeText(this,s, Toast.LENGTH_LONG).show()
    }


}