package com.iec61850bean.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;

import java.io.*;
import java.util.Iterator;


public class ParserDynamicJson {
    public static void parseObject(JSONObject json, String key) {
        //true is exist, otherwise false
        //System.out.println(json.has(key));
        System.out.println(json.get(key));
    }

    public static JSONObject getKey(JSONObject json, String key) {
        JSONObject innerJSON = json;
        boolean exists = json.has(key);
        Iterator<?> keys;
        String nextKeys;

        if (!exists) {
            keys = json.keys();
            while (keys.hasNext()) {
                nextKeys = (String) keys.next();
                try {
                    if (json.get(nextKeys) instanceof JSONObject) {
                        if (exists == false) {
                            innerJSON = json.getJSONObject(nextKeys);
                            getKey(innerJSON, key);
                        }
                    } else if (json.get(nextKeys) instanceof JSONArray) {
                        JSONArray jsonarray = json.getJSONArray(nextKeys);
                        for (int i = 0; i < jsonarray.length(); i++) {
                            String jsonarrayString = jsonarray.get(i).toString();
                            innerJSON = new JSONObject(jsonarrayString);

                            if (exists == false) {
                                getKey(innerJSON, key);
                            }
                        }
                    }
                } catch (Exception e) {

                }
            }


        } else {
            parseObject(json, key);
        }
        return innerJSON;
    }


    public static JSONObject get_value_iterativa(JSONObject json, String target_key) {
        JSONObject json_return = json;
        boolean exists = json.has(target_key);
        System.out.println("la chiave corrente è " + target_key + "? " + exists);
        Iterator<String> keys;
        keys = json_return.keys();
        String key = "";

        while (keys.hasNext()) {
            key = keys.next();
            if(key.equals(target_key)){
                return (JSONObject) json_return.get(key);
            }
            //exists = json.has(target_key);
            System.out.println("CHIAVE: " + key);
            System.out.println(json_return.get(key));
            try {

                if (json.get(key) instanceof JSONObject) {

                    json_return = json_return.getJSONObject(key);
                    key = json_return.keys().next();
                    keys = json_return.keys();
                }
                if (json.get(key) instanceof JSONArray) {
                    JSONArray jsonarray = json_return.getJSONArray(key);
                    for (int i = 0; i < jsonarray.length(); i++) {
                        String jsonarrayString = jsonarray.get(i).toString();
                        json_return = new JSONObject(jsonarrayString);
                        key = json_return.keys().next();
                        keys = json_return.keys();
                    }
                }

            } catch(Exception ex){}


        }
        return null;
    }

    public static JSONObject get_DAType(JSONObject json, String target_id) {
        JSONObject json_return;
        String string_json_array="";

        System.out.println("get_DAType");
        Iterator<String> keys;
        keys = json.keys();
        String key = "";
        key = keys.next();
            //exists = json.has(target_key);
            System.out.println("CHIAVE: " + key);
            try {
                if (json.get(key) instanceof JSONArray) {
                    JSONArray jsonarray = json.getJSONArray(key);
                    for (int i = 0; i < jsonarray.length(); i++) {
                        String jsonarrayString = jsonarray.get(i).toString();
                        string_json_array+=jsonarrayString;
                        //System.out.println("jsonarray.get(i).toString()");
                        //System.out.println(jsonarray.get(i).toString());
                        json_return = new JSONObject(jsonarrayString);
                        //System.out.println("json_return");
                        if(json_return.get("id").equals(target_id)){
                            return json_return;
                        }
                    }
                }
            } catch(Exception ex){}
        return null;
    }

    public static JSONObject get_JSONObject(JSONObject json,String target_key){
        JSONObject json_return = json;
        boolean exists = json.has(target_key);
        Iterator<String> keys_iterator;
        keys_iterator = json_return.keys();
        String key = "";

        while (keys_iterator.hasNext()) {
            key = keys_iterator.next();
            if(key.equals(target_key)){
                return (JSONObject) json_return.get(key);
            }
            try {
                if (json.get(key) instanceof JSONObject || json.get(key) instanceof String ) {
                    json_return = json_return.getJSONObject(key);
                    keys_iterator = json_return.keys();
                }
            } catch(Exception ex){}
        }
        return null;

    }

    public static JSONObject get_JSONArray (JSONObject json,String target_id){
        JSONObject json_return;
        Iterator<String> keys;
        keys = json.keys();
        String key = "";
        key = keys.next();
        //Metti un vettore di stringhe che contiene DAType, DOType etc, tanto questi sono fissi e te li giri tutti
        //exists = json.has(target_key);
        //System.out.println("CHIAVE: " + key);
        try {
            if (json.get(key) instanceof JSONArray) {
                JSONArray jsonarray = json.getJSONArray(key);
                for (int i = 0; i < jsonarray.length(); i++) {
                    String jsonarrayString = jsonarray.get(i).toString();
                    //System.out.println("jsonarray.get(i).toString()");
                    //System.out.println(jsonarray.get(i).toString());
                    json_return = new JSONObject(jsonarrayString);
                    //System.out.println("json_return");
                    if(json_return.get("id").equals(target_id)){
                        return json_return;
                    }
                }
            }
        } catch(Exception ex){}
        return null;
    }


    public static int somma_ricorsiva(int numero) {
        if (numero < 100) {
            System.out.println("Numero prima somma=" + numero);
            numero = numero + numero;
            System.out.println("Numero dopo somma=" + numero);
            return somma_ricorsiva(numero);
        }
        return numero;
    }
    public static JSONObject get_definition(JSONObject json, String target_key, String target_id){
        //target_key are DAType, LNodeType, DOType, EnumType
        //target_id are id key, example INC_1_Mod
        JSONArray json_array= json.getJSONArray(target_key);
        JSONObject json_return;

        for (int i = 0; i < json_array.length(); i++) {
            String jsonarrayString = json_array.get(i).toString();
            json_return = new JSONObject(jsonarrayString);
            if(json_return.get("id").equals(target_id)){
                System.out.println("Elemento Trovato");
                return json_return;
            }
        }
        return null;
    }

    public static void show_id(JSONObject json, String target_key){
        //target_key are DAType, LNodeType, DOType, EnumType
        //target_id are id key, example INC_1_Mod
        JSONArray json_array= json.getJSONArray(target_key);
        JSONObject json_element;
        try{
            for (int i = 0; i < json_array.length(); i++) {
                String jsonarrayString = json_array.get(i).toString();
                json_element = new JSONObject(jsonarrayString);
                System.out.println(json_element.get("id"));
            }
        } catch (Exception ex){ }

    }


    public static void main(String[] args) throws IOException {
        String line, str = null;
        String link = "sample-model.icd";
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(link));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        while ((line = br.readLine()) != null) {
            str += line;
        }

        JSONObject jsondata = XML.toJSONObject(str);
        JSONObject json_prova = XML.toJSONObject(str);
        //int numero=5;

        //numero=somma_ricorsiva(numero);


        //System.out.println("Numero ritornato=" + numero);
        //JSONObject json_object=getKey(jsondata, "DOType");



        /*
        JSONObject jsondata2 = get_value_iterativa(jsondata, "DataTypeTemplates");
        if(jsondata2!=null) System.out.println(jsondata2.toString());
        else System.out.println("Trovato Niente");


        jsondata2 = get_DAType(jsondata2, "AnalogueValue_1");
        if(jsondata2!=null) System.out.println(jsondata2.toString());
        else System.out.println("Trovato Niente2");
        */




        /*
        Iterator<String> keys=jsondata2.keys();
        //System.out.println(jsondata.get("DAType").toString());
        while(keys.hasNext()){
            System.out.println("QUi ci arrivo");
            String key=keys.next();
            System.out.println((key).toString());
            if(jsondata2.get(key) instanceof JSONArray){
                System.out.println(jsondata2.get(key));
            }
        } */

        json_prova=get_JSONObject(json_prova, "DataTypeTemplates");
        System.out.println("GET JSONOBJECT");
        System.out.println(json_prova);
        //CON DAType
        /*System.out.println(json_prova.get("DAType"));
        System.out.println("PROVA get_JSONArray");
        json_prova=get_JSONArray(json_prova, "AnalogueValue_1");
        System.out.println(json_prova);*/
        //CON DOType



        System.out.println("LISTA ID DISPONIBILI");
        show_id(json_prova, "LNodeType");
        System.out.println("GET_DEFINITION");
        JSONObject json_definition=get_definition(json_prova, "LNodeType","LLN01");
        System.out.println(json_definition);









    }
}


