package main.java.com.iec61850bean.app;



import com.beanit.iec61850bean.*;
import com.beanit.iec61850bean.internal.cli.*;

import com.beanit.iec61850bean.internal.scl.DaType;
import org.json.*;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.regex.Matcher;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class Client61850Prova {
    private static final String CONVERT_JSON = "j";
    private static final String CONVERT_JSON_DESCRIPTION = "convert SCL to JSON";
    private static final String PRINT_MODEL_KEY = "m";
    private static final String PRINT_MODEL_KEY_DESCRIPTION = "print model";
    private static final String GET_DATA_VALUES_KEY = "g";
    private static final String GET_DATA_VALUES_KEY_DESCRIPTION = "send GetDataValues request";
    private static final String READ_ALL_DATA_KEY = "ga";
    private static final String READ_ALL_DATA_KEY_DESCRIPTION = "update all data in the model";
    private static final String CREATE_DATA_SET_KEY = "cds";
    private static final String CREATE_DATA_SET_KEY_DESCRIPTION = "create data set";
    private static final String DELETE_DATA_SET_KEY = "dds";
    private static final String DELETE_DATA_SET_KEY_DESCRIPTION = "delete data set";
    private static final String REPORTING_KEY = "r";
    private static final String REPORTING_KEY_DESCRIPTION = "configure reporting";

    private static final StringCliParameter hostParam =
            new CliParameterBuilder("-h")
                    .setDescription("The IP/domain address of the server you want to access.")
                    .setMandatory()
                    .buildStringParameter("host");
    private static final IntCliParameter portParam =
            new CliParameterBuilder("-p")
                    .setDescription("The port to connect to.")
                    .buildIntParameter("port", 102);
    private static final StringCliParameter modelFileParam =
            new CliParameterBuilder("-m")
                    .setDescription(
                            "The file name of the SCL file to read the model from. If this parameter is omitted the model will be read from the server device after connection.")
                    .buildStringParameter("model-file");
    private static final ActionProcessor actionProcessor = new ActionProcessor(new ActionExecutor());
    private static volatile ClientAssociation association;
    private static ServerModel serverModel;

    public static ArrayList<String> topics = new ArrayList();
    public static String logicalDevice;
    public static String pubTopic;
    public static String lnType0;
    public static String lnType;

    public static void main(String[] args) {
        System.out.println("Working Directory = " + System.getProperty("user.dir"));
        List<CliParameter> cliParameters = new ArrayList<>();
        cliParameters.add(hostParam);
        cliParameters.add(portParam);
        cliParameters.add(modelFileParam);

        CliParser cliParser =
                new CliParser(
                        "openiec61850-console-client", "A client application to access IEC 61850 MMS servers.");
        cliParser.addParameters(cliParameters);

        try {
            cliParser.parseArguments(args);
        } catch (CliParseException e1) {
            System.err.println("Error parsing command line parameters: " + e1.getMessage());
            System.out.println(cliParser.getUsageString());
            System.exit(1);
        }

        InetAddress address;
        try {
            address = InetAddress.getByName(hostParam.getValue());
        } catch (UnknownHostException e) {
            System.out.println("Unknown host: " + hostParam.getValue());
            return;
        }

        ClientSap clientSap = new ClientSap();

        try {

            association = clientSap.associate(address, portParam.getValue(), null, new EventListener());
            receiveFile();



        } catch (IOException e) {
            System.out.println("Unable to connect to remote host.");
            return;
        }

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread() {
                            @Override
                            public void run() {
                                association.close();
                            }
                        });

        System.out.println("successfully connected");

        if (modelFileParam.isSelected()) {
            System.out.println("reading model from file...");

            try {
                serverModel = SclParser.parse(modelFileParam.getValue()).get(0);
            } catch (SclParseException e1) {
                System.out.println("Error parsing SCL file: " + e1.getMessage());
                return;
            }

            JSONObject jsonSCL = XML.toJSONObject(modelFileParam.getValue());
            System.out.println("JsonSCL" + jsonSCL);

            association.setServerModel(serverModel);

            System.out.println("successfully read model");

        } else {
            System.out.println("retrieving model...");


            try {

                serverModel = association.retrieveModel();

            } catch (ServiceError e) {
                System.out.println("Service error: " + e.getMessage());
                return;
            } catch (IOException e) {
                System.out.println("Fatal error: " + e.getMessage());
                return;
            }

            System.out.println("successfully read model");
        }


        actionProcessor.addAction(new Action(PRINT_MODEL_KEY, PRINT_MODEL_KEY_DESCRIPTION));

        actionProcessor.addAction(new Action(CONVERT_JSON, CONVERT_JSON_DESCRIPTION));

        actionProcessor.addAction(new Action(GET_DATA_VALUES_KEY, GET_DATA_VALUES_KEY_DESCRIPTION));
        actionProcessor.addAction(new Action(READ_ALL_DATA_KEY, READ_ALL_DATA_KEY_DESCRIPTION));
        actionProcessor.addAction(new Action(CREATE_DATA_SET_KEY, CREATE_DATA_SET_KEY_DESCRIPTION));
        actionProcessor.addAction(new Action(DELETE_DATA_SET_KEY, DELETE_DATA_SET_KEY_DESCRIPTION));
        actionProcessor.addAction(new Action(REPORTING_KEY, REPORTING_KEY_DESCRIPTION));

        actionProcessor.start();
    }

    private static final Pattern TAG_REGEX_IEDNAME = Pattern.compile("<IED name=\"(.+?)\">", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_LDNAME = Pattern.compile("<LDevice inst=\"(.+?)\">", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_LD = Pattern.compile("<LDevice(.+?)</LDevice>", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_LN0 = Pattern.compile("<LN0 lnClass(.+?)</LN0>", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_LN = Pattern.compile("<LN lnClass(.+?)</LN>", Pattern.DOTALL);

    private static final Pattern TAG_REGEX_DO = Pattern.compile("<DO(.+?)/>", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_DOTYPES = Pattern.compile("<DOType(.+?)/DOType>", Pattern.DOTALL);

    private static final Pattern TAG_REGEX_DA = Pattern.compile("<DA(.+?)/>", Pattern.DOTALL);


    private static final Pattern TAG_REGEX_DATYPES = Pattern.compile("<DAType(.+?)/DAType>", Pattern.DOTALL);

    private static final Pattern TAG_REGEX_DATATYPETEMPLATES = Pattern
            .compile("<DataTypeTemplates>(.+?)</DataTypeTemplates>", Pattern.DOTALL);

    private static final Pattern TAG_REGEX_LNTYPE = Pattern.compile("<LNodeType(.+?)</LNodeType>", Pattern.DOTALL);

    private static final Pattern TAG_REGEX_ENUMTYPE = Pattern.compile("<EnumType(.+?)</EnumType>", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_ENUMVAL = Pattern.compile("<EnumVal(.+?)</EnumVal>", Pattern.DOTALL);


    private static final ArrayList<String> topicDisponibili = new ArrayList<String>();


    private static void receiveFile() throws IOException {
        Socket socket = null;
        int maxsize = 999999999;
        int byteread;
        int current = 0;

        byte[] buffer = new byte[maxsize];
        socket = new Socket("localhost", 9099);
        InputStream is = socket.getInputStream();
        File test = new File("model.icd");
        test.createNewFile();
        FileOutputStream fos = new FileOutputStream(test);
        BufferedOutputStream out = new BufferedOutputStream(fos);
        byteread = is.read(buffer, 0, buffer.length);
        current = byteread;
        do{
            byteread = is.read(buffer, 0, buffer.length - current);
            if (byteread >= 0) current += byteread;
        } while (byteread > -1);
        out.write(buffer, 0, current);
        out.flush();

        socket.close();
        fos.close();
        is.close();
    }

    /*QUESTE FUNZIONI VENGONO UTILIZZATE DAL CLIENT 61850 PER SELEZIONARE I PEZZI SCL DI INTERESSE*/
    private static String getIED(final String str) {
        final Matcher matcher = TAG_REGEX_IEDNAME.matcher(str);
        String iedName = "";
        while (matcher.find()) {
            iedName = matcher.group(1);
        }
        return iedName;
    }

    private static String getLogicalDeviceName(final String str) {
        final Matcher matcher = TAG_REGEX_LDNAME.matcher(str);
        String ldName = "";
        while (matcher.find()) {
            ldName = matcher.group(1);
        }
        return ldName;
    }

    private static ArrayList<String> getLogicalDevice(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_LD.matcher(str);
        while (matcher.find()) {
            tagValues.add("<LDevice" + matcher.group(1) + "</LDevice>");
        }
        return tagValues;
    }

    private static String getLogicalNodeZero(final String str) {
        final Matcher matcher = TAG_REGEX_LN0.matcher(str);
        String lnodezero = "";
        while (matcher.find()) {
            lnodezero = "<LN0 lnClass" + matcher.group(1) + "</LN0>";
        }
        return lnodezero;
    }

    private static ArrayList<String> getLogicalNodes(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_LN.matcher(str);
        while (matcher.find()) {
            tagValues.add("<LN lnClass" + matcher.group(1) + "</LN>");
        }
        return tagValues;
    }

    private static String getDTTemplates(final String str) {
        final Matcher matcher = TAG_REGEX_DATATYPETEMPLATES.matcher(str);
        String dataTypeTemplates = "";
        while (matcher.find()) {
            dataTypeTemplates = "<DataTypeTemplates>" + matcher.group(1) + "</DataTypeTemplates>";
        }
        return dataTypeTemplates;
    }

    private static ArrayList<String> getLNTypeValues(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_LNTYPE.matcher(str);
        while (matcher.find()) {
            tagValues.add("<LNodeType" + matcher.group(1) + "</LNodeType>");
        }
        return tagValues;
    }

    private static ArrayList<String> getDO(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_DO.matcher(str);
        while (matcher.find()) {
            tagValues.add("<DO" + matcher.group(1) + "/>");
        }
        return tagValues;
    }

    private static ArrayList<String> getDOTypes(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_DOTYPES.matcher(str);
        while (matcher.find()) {
            tagValues.add("<DOType" + matcher.group(1) + "/DOType>");
        }
        return tagValues;
    }

    private static ArrayList<String> getDA(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_DA.matcher(str);
        while (matcher.find()) {
            tagValues.add("<DA" + matcher.group(1) + "/>");
        }
        return tagValues;
    }



    private static ArrayList<String> getDATypes(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_DATYPES.matcher(str);
        while (matcher.find()) {
            tagValues.add("<DAType" + matcher.group(1) + "/DAType>");
        }
        return tagValues;
    }

    private static ArrayList<String> getEnumTypes(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_ENUMTYPE.matcher(str);
        while (matcher.find()) {
            tagValues.add("<EnumType" + matcher.group(1) + "</EnumType>");
        }
        return tagValues;
    }


    private static ArrayList<String> getEnumVal(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_ENUMVAL.matcher(str);
        while (matcher.find()) {
            tagValues.add("<EnumVal" + matcher.group(1) + "</EnumVal>");
        }
        return tagValues;
    }


    private static String make_topic(ArrayList<String> level, ArrayList<String> objects){
        String first = String.join("/", level);
        String second = String.join(".", objects);
        String topic=first+"/"+second;
        return topic;
    }


    private static void publishMQTT(MqttClient sampleClient, String topic, JSONObject json, int qos){
        MqttMessage message = new MqttMessage(json.toString().getBytes());
        message.setQos(qos);
        message.setRetained(true);
        try {
            sampleClient.publish(topic, message);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    private static class EventListener implements ClientEventListener {

        @Override
        public void newReport(Report report) {
            System.out.println("\n----------------");
            System.out.println("Received report: ");
            System.err.println(report);
            System.out.println("------------------");
        }

        @Override
        public void associationClosed(IOException e) {
            System.out.print("Received connection closed signal. Reason: ");
            if (!e.getMessage().isEmpty()) {
                System.out.println(e.getMessage());
            } else {
                System.out.println("unknown");
            }
            actionProcessor.close();
        }
    }

    private static class ActionExecutor implements ActionListener {

        @Override
        public void actionCalled(String actionKey) throws ActionException {
            try {

                switch (actionKey) {
                    case PRINT_MODEL_KEY:
                        System.out.println(serverModel);
                        break;
                    case CONVERT_JSON:
                        ArrayList<String> level=new ArrayList<>();
                        ArrayList<String> objects=new ArrayList<>();
                        /*DEFINIZIONE CLIENT MQTT LATO GATEWAY*/
                        int qos = 2;
                        String broker = "tcp://localhost:1883";
                        String clientId = "clientIEC61850";
                        MemoryPersistence persistence = new MemoryPersistence();
                        MqttClient sampleClient = new MqttClient(broker, clientId, persistence);
                        MqttConnectOptions connOpts = new MqttConnectOptions();
                        connOpts.setCleanSession(false);
                        sampleClient.connect(connOpts);
                        /*FINE DEFINIZIONE CLIENT MQTT*/

                        /*QUI AVVIENE LA LETTURA DA FILE SCL*/
                        String line = null;
                        String str = "";
                        String link = "model.icd";
                        BufferedReader br = new BufferedReader(new FileReader(link));
                        while ((line = br.readLine()) != null) {
                            str += line;
                        }
                        /*FINE LETTURA FILE SCL*/

                        /*OTTENGO IL NOME DELL'IED DAL FILE SCL*/
                        String iedName = getIED(str);
                        level.add(iedName);
                        //System.out.println("Level="+level.toString());;
                        pubTopic = iedName + "/";

                        //OTTENGO LA LISTA DEI LOGICAL DEVICE PRESENTI NEL FILE SCL
                        ArrayList<String> lDevices = getLogicalDevice(str);

                        //OTTENGO LA PARTE RELATIVA AL DATA TYPE TEMPLATES E LA SALVO IN UNA STRINGA
                        String dataTypeTemplates = getDTTemplates(str);

                        //IN DEGLI ARRAYLIST DI TIPO STRINGA SALVO GLI ELEMENTI DEL TIPO <LNType ...></LNType>, <DOType ...></DOType> e <DAType ...></DAType>
                        ArrayList<String> LNTypes = getLNTypeValues(dataTypeTemplates);
                        ArrayList<String> DOTypes = getDOTypes(dataTypeTemplates);
                        ArrayList<String> DATypes = getDATypes(dataTypeTemplates);
                        ArrayList<String> EnumTypes = getEnumTypes(dataTypeTemplates);

                        //SI SCORRE LA LISTA DEI LOGICAL DEVICE PRECEDENTEMENTE INIZIALIZZATA ALLA RIGA 341
                        for (int i = 0; i < lDevices.size(); i++)
                        {
                            logicalDevice = lDevices.get(i); //OTTENGO LA PARTE SCL DEL LOGICAL DEVICE CORRENTE
                            String logicalDeviceName = getLogicalDeviceName(logicalDevice); //OTTENGO IL NOME DEL LOGICAL DEVICE CORRENTE
                            level.add(logicalDeviceName);
                            //System.out.println("Level="+level.toString());
                            pubTopic = pubTopic + logicalDeviceName + "/"; //E IL NOME DEL LOGICAL DEVICE CORRENTE LO CONCATENO ALLA STRINGA RELATIVA AL TOPIC
                            //CHE CONTIENE GIA' IL NOME DELL'IED

                            String ln0 = getLogicalNodeZero(logicalDevice); //QUI SALVO LA PARTE SCL RELATIVA AL NODO LOGICO ZERO
                            ArrayList<String> lNodes = getLogicalNodes(logicalDevice); //QUI SALVO LA PARTE SCL RELATIVA AGLI ALTRI NODI LOGICI

                            JSONObject jsondataLN0 = XML.toJSONObject(ln0); //LA PARTE SCL RELATIVA AL NODO LOGICO ZERO LA CONVERTO IN UN OGGETTO JSON
                            lnType0 = jsondataLN0.getJSONObject("LN0").getString("lnType"); //DELL'OGGETTO JSON APPENA OTTENUTO RICAVO IL VALORE DELL'ATTRIBUTO lnType (IN QUESTO CASO SARA' LLN01)


                            //Si Scorre la lista dei DAType
                            for(int q=0; q< DATypes.size(); q++){
                                String dAType = DATypes.get(q);

                                JSONObject jsonDAType = XML.toJSONObject(dAType); //pezzo json da pubblicare sul topic
                                String idDAType = jsonDAType.getJSONObject("DAType").getString("id");

                                //crea funzione che prende jsonDAType.toString() e poi chiami .getBytes() dentro la funzione
                                objects.add(idDAType);
                                //System.out.println("objects= "+objects.toString());
                                //String publishTopic = pubTopic+idDAType;
                                String publishTopic = make_topic(level, objects);

                                publishMQTT(sampleClient, publishTopic, jsonDAType, qos);
                                objects.remove(idDAType);

                                topicDisponibili.add(publishTopic);

                            }

                            //Si scorre ogni EnumType
                            for(int q=0; q<EnumTypes.size(); q++){
                                String EnumType = EnumTypes.get(q);
                                JSONObject jsondataEnumVal = XML.toJSONObject(EnumType);

                                String idEnumType = jsondataEnumVal.getJSONObject("EnumType").getString("id");
                                level.add(idEnumType);
                                //System.out.println("Level="+level.toString());
                                //String publish2Topic = pubTopic+idEnumType;

                                String publish2Topic = make_topic(level, objects);
                                publishMQTT(sampleClient, publish2Topic, jsondataEnumVal, qos);
                                level.remove(idEnumType);
                                topicDisponibili.add(publish2Topic);

                            }

                            objects.add(lnType0);
                            //System.out.println("Level="+level.toString());
                            pubTopic = pubTopic + lnType0; //E LA CONCATENO AL TOPIC

                            /*NODO LOGICO ZERO*/
                            for (int q = 0; q < LNTypes.size(); q++) //SI SCORRE LA LISTA DEI <LNType ...></LNType>
                            {
                                String LNodeType = LNTypes.get(q); //SI OTTIENE L'ELEMENTO SPECIFICO <LNType ...></LNType> DELL'ITERAZIONE CORRENTE
                                JSONObject jsondataLNType = XML.toJSONObject(LNodeType); //L'ELEMENTO OTTENUTO VIENE CONVERTITO IN JSON
                                String id = jsondataLNType.getJSONObject("LNodeType").getString("id"); //E DI QUESTO ELEMENTO JSON SI OTTIENE L'ID (ESEMPIO: <LNodeType id="LLN01"...>)
                                if (id.equals(lnType0)) { //SE L'UGUAGLIANZA E' VERIFICATA, OSSIA SE LNodeType id="LLN01" == lnType0 (vedi riga 363)
                                    MqttMessage messageLNType = new MqttMessage(jsondataLNType.toString().getBytes()); //VIENE CREATO UN NUOVO MESSAGGIO MQTT CHE PRENDE IN INGRESSO
                                    messageLNType.setQos(qos);   //VIENE SETTATA LA QOS (IN QUESTO CASO 2)             //L'OGGETTO JSON DELLA RIGA 370 IN FORMATO STRINGA (LO PRENDE SOLO COSì)
                                    messageLNType.setRetained(true); //VIENE SETTATO A RETAIN IN MODO CHE IL CLIENT MQTT ESTERNO POSSA OTTENERE I VALORI DI INTERESSE ANCHE SI CONNETTE IN UN SECONDO MOMENTO
                                    sampleClient.publish(pubTopic, messageLNType); //E QUINDI LA TIPIZZAZIONE DI QUEL NODO VIENE PUBBLICATA SUL TOPIC DELLA RIGA 364
                                    topicDisponibili.add(pubTopic);

                                    //QUINDI SI PASSA AI DATA OBJECT
                                    ArrayList<String> dataObjects = getDO(LNodeType); //SI PASSA L'ELEMENTO <LNType ...></LNType> OTTENUTO ALLA RIGA 369, PER SALVARE OGNI DATA OBJECT CONTENUTO AL SUO INTERNO IN UN ARRAY LIST
                                    for (int j = 0; j < dataObjects.size(); j++) { //SI SCORRE QUEST'ARRAYLIST
                                        String DataObject = dataObjects.get(j); //E SI OTTIENE IL DATA OBJECT <DO name=.../> RELATIVO ALL'ITERAZIONE CORRENTE
                                        JSONObject jsondataDO = XML.toJSONObject(DataObject); //QUESTO PEZZO DI SCL <DO name=... type=/> VIENE CONVERTITO IN JSON

                                        String nameDO = jsondataDO.getJSONObject("DO").getString("name"); //DI QUESTO DATA OBJECT VENGONO OTTENUTI IL name
                                        String typeDO = jsondataDO.getJSONObject("DO").getString("type");//E IL type

                                        objects.add(nameDO);
                                        //System.out.println("objects= "+objects.toString());
                                        //pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType0 + "." + nameDO; //IL name VIENE USATO PER LA COSTRUZIONE DINAMICA DEL TOPIC
                                        //SU CUI PUBBLICARE IL DATA OBJECT CORRENTE

                                        String pubTopic = make_topic(level, objects);
                                        publishMQTT(sampleClient, pubTopic, jsondataDO, qos);
                                        topicDisponibili.add(pubTopic);

                                        for (int y = 0; y < DOTypes.size(); y++) { //SUCCESSIVAMENTE SI SCORRE LA LISTA DEI <DOType ...></DOType> (SONO I PEZZI SCL DEI DOType)
                                            String DataObjectTypes = DOTypes.get(y); //SI OTTIENE L'ELEMENTO DOType SCL RELATIVO ALL'ITERAZIONE CORRENTE

                                            JSONObject jsondataDOTypes = XML.toJSONObject(DataObjectTypes); //  QUESTO ELEMENTO VIENE CONVERTITO IN JSON

                                            if (jsondataDOTypes.get("DOType") instanceof JSONArray) { //SE L'ELEMENTO OTTENUTO E' UN JSON ARRAY
                                                //SCINDO L'ARRAY IN TANTI ELEMENTI PIU' SEMPLICI
                                                JSONArray recs = jsondataDOTypes.getJSONArray("DOType");

                                                System.out.println(recs);

                                                for (int z = 0; z < recs.length(); z++) {
                                                    JSONObject rec = recs.getJSONObject(z);

                                                    String jsonString = "{\"DOType\":" + rec.toString() + "}";
                                                    JSONObject jsonObject = new JSONObject(jsonString);

                                                    String idDO = jsonObject.getJSONObject("DOType").getString("id");

                                                    if (idDO.equals(typeDO)) {

                                                        ArrayList<String> DAttributes = getDA(DataObjectTypes);

                                                        for (int f = 0; f < DAttributes.size(); f++) {
                                                            JSONObject jsondataDAttribute = XML.toJSONObject(DAttributes.get(f));
                                                            String nameDA = jsondataDAttribute.getJSONObject("DA").getString("name");

                                                            objects.add(nameDA);
                                                            //System.out.println("objects= "+objects.toString());
                                                            //pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType0 + "." + nameDO + "." + nameDA;

                                                            pubTopic = make_topic(level, objects);
                                                            publishMQTT(sampleClient, pubTopic, jsondataDAttribute, qos);
                                                            topicDisponibili.add(pubTopic);
                                                            objects.remove(nameDA);
                                                        }
                                                    }
                                                }

                                            } else {
                                                //Del data object type ottenuto ricavo l'id
                                                String idDO = jsondataDOTypes.getJSONObject("DOType").getString("id");
                                                if (idDO.equals(typeDO)) { //e verifico l'uguaglianza
                                                    ArrayList<String> DAttributes = getDA(DataObjectTypes);
                                                    for (int f = 0; f < DAttributes.size(); f++) {
                                                        JSONObject jsondataDAttribute = XML.toJSONObject(DAttributes.get(f));
                                                        String nameDA = jsondataDAttribute.getJSONObject("DA").getString("name");
                                                        objects.add(nameDA);
                                                        //System.out.println("objects= "+objects.toString());
                                                        //pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType0 + "." + nameDO + "." + nameDA;

                                                        pubTopic = make_topic(level, objects);
                                                        publishMQTT(sampleClient, pubTopic, jsondataDAttribute, qos);
                                                        topicDisponibili.add(pubTopic);
                                                        objects.remove(nameDA);
                                                    }
                                                }
                                            }
                                        }
                                        objects.remove(nameDO);
                                    }
                                }
                            }

                            objects.remove(lnType0);

                            /*ALTRI NODI LOGICI*/
                            for (int q = 0; q < LNTypes.size(); q++) { // 1 <LNodeType id="LLN01" lnClass="LLN0"> .....
                                String LNodeType = LNTypes.get(q); //Prendo il pezzo di <LNodeType dell'iterazione corrente
                                JSONObject jsondataLNType = XML.toJSONObject(LNodeType);

                                ArrayList<String> dataObject = getDO(LNodeType); //e glielo passo come parametro a questo metodo
                                //per ottenere la lista di tutti i data object in esso contenuti
                                String idLNType = jsondataLNType.getJSONObject("LNodeType").getString("id");

                                /*           String LNodeType = LNTypes.get(q);
                                JSONObject jsondataLNType = XML.toJSONObject(LNodeType);
                                String id = jsondataLNType.getJSONObject("LNodeType").getString("id");*/
                                for (int j = 0; j < lNodes.size(); j++) { // <LN lnClass="LPHD" lnType="LPHD1" inst="1" prefix="" />
                                    String logicalNode = lNodes.get(j);

                                    JSONObject jsondataLN = XML.toJSONObject(logicalNode);

                                    if (jsondataLN.get("LN") instanceof JSONArray) {
                                        JSONArray jsonArrayLN = jsondataLN.getJSONArray("LN");

                                        for (int k = 0; k < jsonArrayLN.length(); k++) {
                                            JSONObject jsonLN = jsonArrayLN.getJSONObject(k);
                                            String jsonLNString = "{\"LN\":" + jsonLN.toString() + "}";
                                            JSONObject jsonObjectLN = new JSONObject(jsonLNString);

                                            String lnType = jsonObjectLN.getJSONObject("LN").getString("lnType");

                                            if(idLNType.equals(lnType)) {
                                                objects.add(lnType);
                                                //System.out.println("Level="+level.toString());
                                                //pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType;

                                                pubTopic = make_topic(level, objects);
                                                publishMQTT(sampleClient, pubTopic, jsondataLNType, qos);
                                                topicDisponibili.add(pubTopic);


                                                for(int t = 0; t <dataObject.size(); t++) {
                                                    JSONObject jsonDO = XML.toJSONObject(dataObject.get(t)); //pezzo json da pubblicare sul topic
                                                    String doName = jsonDO.getJSONObject("DO").getString("name");
                                                    String doType = jsonDO.getJSONObject("DO").getString("type");
                                                    objects.add(doName);
                                                    //System.out.println("objects= "+objects.toString());
                                                    //pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType+"."+doName;

                                                    pubTopic = make_topic(level, objects);
                                                    publishMQTT(sampleClient, pubTopic, jsonDO, qos);
                                                    topicDisponibili.add(pubTopic);

                                                    for(int ii = 0; ii<DOTypes.size(); ii++) {
                                                        String dataObjectType = DOTypes.get(ii);

                                                        JSONObject jsonDOType = XML.toJSONObject(dataObjectType); //pezzo json da pubblicare sul topic

                                                        String idDOType = jsonDOType.getJSONObject("DOType").getString("id");


                                                        if(doType.equals(idDOType)) {
                                                            ArrayList<String> dataAttribute = getDA(dataObjectType);

                                                            for(int jj = 0; jj<dataAttribute.size(); jj++) {
                                                                JSONObject jsonDA = XML.toJSONObject(dataAttribute.get(jj));
                                                                String dataAttributeName = jsonDA.getJSONObject("DA").getString("name");
                                                                objects.add(dataAttributeName);
                                                                //System.out.println("objects="+objects.toString());
                                                                //pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType+"."+doName+"."+dataAttributeName;

                                                                pubTopic = make_topic(level, objects);
                                                                publishMQTT(sampleClient, pubTopic, jsonDA, qos);

                                                                topicDisponibili.add(pubTopic);
                                                                objects.remove(dataAttributeName);
                                                            }

                                                        }
                                                    }
                                                    objects.remove(doName);
                                                }
                                                objects.remove(lnType);
                                            }
                                        }
                                    } else {
                                        String lnType = jsondataLN.getJSONObject("LN").getString("lnType");

                                        if(idLNType.equals(lnType)) {
                                            objects.add(lnType);
                                            //System.out.println("Level="+level.toString());
                                            //pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType;

                                            pubTopic = make_topic(level, objects);
                                            publishMQTT(sampleClient, pubTopic, jsondataLNType, qos);

                                            topicDisponibili.add(pubTopic);

                                            for(int t = 0; t <dataObject.size(); t++) {
                                                JSONObject jsonDO = XML.toJSONObject(dataObject.get(t)); //pezzo json da pubblicare sul topic
                                                String doName = jsonDO.getJSONObject("DO").getString("name");
                                                String doType = jsonDO.getJSONObject("DO").getString("type");

                                                objects.add(lnType);
                                                //System.out.println("Level="+level.toString());
                                                pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType+"/"+doName;
                                                topicDisponibili.add(pubTopic);

                                                for(int ii = 0; ii<DOTypes.size(); ii++) {
                                                    String dataObjectType = DOTypes.get(ii);
                                                    JSONObject jsonDOType = XML.toJSONObject(dataObjectType); //pezzo json da pubblicare sul topic
                                                    String idDOType = jsonDOType.getJSONObject("DOType").getString("id");

                                                    if(doType.equals(idDOType)) {
                                                        ArrayList<String> dataAttribute = getDA(dataObjectType);
                                                        System.out.println(dataAttribute);
                                                        for(int jj = 0; jj<dataAttribute.size(); jj++) {
                                                            JSONObject jsonDA = XML.toJSONObject(dataAttribute.get(jj));
                                                            String dataAttributeName = jsonDA.getJSONObject("DA").getString("name");
                                                            objects.add(dataAttributeName);
                                                            //System.out.println("Level="+level.toString());
                                                            pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType+"."+doName+"."+dataAttributeName;
                                                            topicDisponibili.add(pubTopic);
                                                            objects.remove(dataAttributeName);
                                                        }
                                                    }
                                                }
                                                objects.remove(lnType);
                                            }

                                        }

                                    }
                                }
                            }

                        }

                        for (int counter = 0; counter < topicDisponibili.size(); counter++) {
                            System.out.println(topicDisponibili.get(counter)+"\n");
                        }
                        break;
                    case READ_ALL_DATA_KEY:
                        System.out.print("Reading all data...");
                        try {
                            association.getAllDataValues();
                        } catch (ServiceError e) {
                            System.err.println("Service error: " + e.getMessage());
                        }
                        System.out.println("done");
                        break;
                    case GET_DATA_VALUES_KEY: {
                        if (serverModel == null) {
                            System.out.println("You have to retrieve the model before reading data.");
                            return;
                        }

                        FcModelNode fcModelNode = askForFcModelNode();

                        System.out.println("Sending GetDataValues request...");

                        try {
                            association.getDataValues(fcModelNode);
                        } catch (ServiceError e) {
                            System.out.println("Service error: " + e.getMessage());
                            return;
                        } catch (IOException e) {
                            System.out.println("Fatal error: " + e.getMessage());
                            return;
                        }

                        System.out.println("Successfully read data.");
                        System.out.println(fcModelNode);

                        break;
                    }
                    case CREATE_DATA_SET_KEY: {
                        System.out.println(
                                "Enter the reference of the data set to create (e.g. myld/MYLN0.dataset1): ");
                        String reference = actionProcessor.getReader().readLine();

                        System.out.println("How many entries shall the data set have: ");
                        String numberOfEntriesString = actionProcessor.getReader().readLine();
                        int numDataSetEntries = Integer.parseInt(numberOfEntriesString);

                        List<FcModelNode> dataSetMembers = new ArrayList<>();
                        for (int i = 0; i < numDataSetEntries; i++) {
                            dataSetMembers.add(askForFcModelNode());
                        }

                        DataSet dataSet = new DataSet(reference, dataSetMembers);
                        System.out.print("Creating data set..");
                        association.createDataSet(dataSet);
                        System.out.println("done");

                        break;
                    }
                    case DELETE_DATA_SET_KEY: {
                        System.out.println(
                                "Enter the reference of the data set to delete (e.g. myld/MYLN0.dataset1): ");
                        String reference = actionProcessor.getReader().readLine();

                        DataSet dataSet = serverModel.getDataSet(reference);
                        if (dataSet == null) {
                            throw new ActionException("Unable to find data set with the given reference.");
                        }
                        System.out.print("Deleting data set..");
                        association.deleteDataSet(dataSet);
                        System.out.println("done");

                        break;
                    }
                    case REPORTING_KEY: {
                        System.out.println("Enter the URCB reference: ");
                        String reference = actionProcessor.getReader().readLine();
                        Urcb urcb = serverModel.getUrcb(reference);
                        if (urcb == null) {
                            Brcb brcb = serverModel.getBrcb(reference);
                            if (brcb != null) {
                                throw new ActionException(
                                        "Though buffered reporting is supported by the library it is not yet supported by the console application.");
                            }
                            throw new ActionException("Unable to find RCB with the given reference.");
                        }

                        while (true) {
                            association.getRcbValues(urcb);
                            System.out.println();
                            System.out.println(urcb);
                            System.out.println();
                            System.out.println("What do you want to configure?");
                            System.out.println("1 - reserve");
                            System.out.println("2 - cancel reservation");
                            System.out.println("3 - enable");
                            System.out.println("4 - disable");
                            System.out.println("5 - set data set");
                            System.out.println("6 - set trigger options");
                            System.out.println("7 - set integrity period");
                            System.out.println("8 - send general interrogation");
                            System.out.println("0 - quit");
                            try {
                                int rcbAction = Integer.parseInt(actionProcessor.getReader().readLine());
                                switch (rcbAction) {
                                    case 0:
                                        return;
                                    case 1:
                                        System.out.print("Reserving RCB..");
                                        association.reserveUrcb(urcb);
                                        System.out.println("done");
                                        break;
                                    case 2:
                                        System.out.print("Canceling RCB reservation..");
                                        association.cancelUrcbReservation(urcb);
                                        System.out.println("done");
                                        break;
                                    case 3:
                                        System.out.print("Enabling reporting..");
                                        association.enableReporting(urcb);
                                        System.out.println("done");
                                        break;
                                    case 4:
                                        System.out.print("Disabling reporting..");
                                        association.disableReporting(urcb);
                                        System.out.println("done");
                                        break;
                                    case 5: {
                                        System.out.print("Set data set reference:");
                                        String dataSetReference = actionProcessor.getReader().readLine();
                                        urcb.getDatSet().setValue(dataSetReference);
                                        List<ServiceError> serviceErrors =
                                                association.setRcbValues(
                                                        urcb, false, true, false, false, false, false, false, false);
                                        if (serviceErrors.get(0) != null) {
                                            throw serviceErrors.get(0);
                                        }
                                        System.out.println("done");
                                        break;
                                    }
                                    case 6: {
                                        System.out.print(
                                                "Set the trigger options (data change, data update, quality change, interity, GI):");
                                        String triggerOptionsString = actionProcessor.getReader().readLine();
                                        String[] triggerOptionsStrings = triggerOptionsString.split(",");
                                        BdaTriggerConditions triggerOptions = urcb.getTrgOps();
                                        triggerOptions.setDataChange(
                                                Boolean.parseBoolean(triggerOptionsStrings[0]));
                                        triggerOptions.setDataUpdate(
                                                Boolean.parseBoolean(triggerOptionsStrings[1]));
                                        triggerOptions.setQualityChange(
                                                Boolean.parseBoolean(triggerOptionsStrings[2]));
                                        triggerOptions.setIntegrity(Boolean.parseBoolean(triggerOptionsStrings[3]));
                                        triggerOptions.setGeneralInterrogation(
                                                Boolean.parseBoolean(triggerOptionsStrings[4]));
                                        List<ServiceError> serviceErrors =
                                                association.setRcbValues(
                                                        urcb, false, false, false, false, true, false, false, false);
                                        if (serviceErrors.get(0) != null) {
                                            throw serviceErrors.get(0);
                                        }
                                        System.out.println("done");
                                        break;
                                    }
                                    case 7: {
                                        System.out.print("Specify integrity period in ms:");
                                        String integrityPeriodString = actionProcessor.getReader().readLine();
                                        urcb.getIntgPd().setValue(Long.parseLong(integrityPeriodString));
                                        List<ServiceError> serviceErrors =
                                                association.setRcbValues(
                                                        urcb, false, false, false, false, false, true, false, false);
                                        if (serviceErrors.get(0) != null) {
                                            throw serviceErrors.get(0);
                                        }
                                        System.out.println("done");
                                        break;
                                    }
                                    case 8:
                                        System.out.print("Sending GI..");
                                        association.startGi(urcb);
                                        System.out.println("done");
                                        break;
                                    default:
                                        System.err.println("Unknown option.");
                                        break;
                                }
                            } catch (ServiceError e) {
                                System.err.println("Service error: " + e.getMessage());
                            } catch (NumberFormatException e) {
                                System.err.println("Cannot parse number: " + e.getMessage());
                            }
                        }
                    }
                    default:
                        break;
                }
            } catch (Exception e) {
                throw new ActionException(e);
            }
        }

        private FcModelNode askForFcModelNode() throws IOException, ActionException {
            System.out.println("Enter reference (e.g. myld/MYLN0.do.da.bda): ");
            String reference = actionProcessor.getReader().readLine();
            System.out.println("Enter functional constraint of referenced node: ");
            String fcString = actionProcessor.getReader().readLine();

            Fc fc = Fc.fromString(fcString);
            if (fc == null) {
                throw new ActionException("Unknown functional constraint.");
            }

            ModelNode modelNode = serverModel.findModelNode(reference, Fc.fromString(fcString));
            if (modelNode == null) {
                throw new ActionException(
                        "A model node with the given reference and functional constraint could not be found.");
            }

            if (!(modelNode instanceof FcModelNode)) {
                throw new ActionException(
                        "The given model node is not a functionally constraint model node.");
            }

            FcModelNode fcModelNode = (FcModelNode) modelNode;
            return fcModelNode;
        }

        @Override
        public void quit() {
            System.out.println("** Closing connection.");
            association.close();
            return;
        }
    }
}
