
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import javafx.util.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import pt.lsts.imc.RemoteSensorInfo;
import pt.lsts.imc.net.ConnectFilter;
import pt.lsts.imc.net.IMCProtocol;
import pt.lsts.util.WGS84Utilities;


import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;


public class Positions {

    public static int imcid;
    public static String sensorClass;
    public static IMCProtocol proto;


    public static void main(String[] args) throws Exception {

        get();
    }

    public static void get() throws IOException{


        boolean fetchAUVType = true;

        boolean fetchGliderType = true;

        boolean fetchDrifterType = true;

        boolean fetchShipType = true;

        boolean fetchAISType = true;
        final String USER_AGENT = "Mozilla/5.0";




        HashMap<String, String> typeRqstLst = new LinkedHashMap<>();
        if (fetchAUVType){
            typeRqstLst.put("auv/", "AUV");
            sensorClass= "AUV";}
        if (fetchGliderType) {
            typeRqstLst.put("glider/", "GLIDER");
            sensorClass= "Glider";
        }
        if (fetchDrifterType) {
            typeRqstLst.put("drifter/", "DRIFTER");
            sensorClass= "Drifter";
        }
        if (fetchShipType) {
            typeRqstLst.put("ship/", "SHIP");
            sensorClass= "Ship";
        }
        if (fetchAISType) {
            typeRqstLst.put("uav/", "AIS");
            sensorClass= "AIS";
        }

        for (String typeRqst : typeRqstLst.keySet()) {

            String url = "http://odss.mbari.org/trackingdb/" + "positionOfType/" + typeRqst + "last/" + 3 + "h/data.html";


            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            // optional default is GET
            con.setRequestMethod("GET");

            //add request header
            con.setRequestProperty("User-Agent", USER_AGENT);

            int responseCode = con.getResponseCode();
            System.out.println("\nSending 'GET' request to URL : " + url);
            System.out.println("Response Code : " + responseCode);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();


            parsing(response.toString());

        }
    }

    private static void parsing(String resp) throws IOException {
        LinkedHashMap<String, Pair<Double, Double>> vehicleData = new LinkedHashMap<>();

        String latitude;
        String longitude;
        String previous=null;
        String current=null;
        LinkedHashMap<String, Integer> data= new LinkedHashMap<>();
        org.jsoup.nodes.Document doc = Jsoup.parse(resp);

        Elements links = doc.select("td");
        if (links.size() > 5) {

            for(int i=0; i < links.size(); i=i+5){

                if(i==0){
                    previous = String.valueOf(links.get(i ));
                    previous=previous.substring(previous.indexOf(">") +1, previous.indexOf("/") -1);

                }

                current = String.valueOf(links.get(i ));
                current= current.substring(current.indexOf(">") +1, current.indexOf("/") -1);
                if(!previous.equals(current)){
                    latitude=links.get(i-3).toString();
                    latitude=latitude.replaceAll("[^\\.0123456789-]","");
                    double lat= Double.parseDouble(latitude);

                    longitude=links.get(i-2).toString();
                    longitude=longitude.replaceAll("[^\\.0123456789-]","");
                    double lon= Double.parseDouble(longitude);
                    vehicleData.put(previous, new Pair<>( lat,lon));
                }
                if(links.get(i+4).equals(links.last())){

                    latitude=links.get(i+2).toString();
                    latitude=latitude.replaceAll("[^\\.0123456789-]","");
                    double lat= Double.parseDouble(latitude);
                    longitude=links.get(i+3).toString();
                    longitude=longitude.replaceAll("[^\\.0123456789-]","");
                    double lon= Double.parseDouble(longitude);
                    vehicleData.put(current, new Pair<>(lat, lon));

                }
                previous = current;

                synchronized (data) {
                    data.put(current, ++imcid);
                }

            }





            publishToNeptus(vehicleData, data);
            publishToRipples(vehicleData, data);



        }


    }




    private static void publishToNeptus(LinkedHashMap<String, Pair<Double, Double>> vehicleData, LinkedHashMap<String, Integer> data){


   /*  RemoteSensorInfo info = new RemoteSensorInfo();

        info.setLat(Math.toRadians(lat));
        info.setLon(Math.toRadians(lon));

        info.setHeading((float) Math.atan2(ySpeed, xSpeed));

//baseado na ultima posicao calcular essa deslocacao
        info.setSensorClass(sensorClass);
        info.setId(vehicle);
        try {

            proto.setAutoConnect(ConnectFilter.CCUS_ONLY);
            proto.sendToPeers(info);
            System.out.println("Posting " + info);
        } catch (Exception e) {
            e.printStackTrace();
        }
*/



    }



    private static void publishToRipples(LinkedHashMap<String, Pair<Double, Double>> vehicleData, LinkedHashMap<String, Integer> data) throws IOException {
        String ripplesUrl = "http://ripples.lsts.pt/api/v1/systems";
        SimpleDateFormat fmt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ssZ");

        JsonObject obj = new JsonObject();


        for (Map.Entry<String, Integer> entry : data.entrySet()) {

            obj.add("imcid", entry.getValue());

        }
        for(Map.Entry<String,Pair<Double ,Double >>entry2: vehicleData.entrySet()) {

            obj.add("name", entry2.getKey());

        }
        for(Map.Entry<String,Pair<Double, Double >>entry2: vehicleData.entrySet()) {

            obj.add("coordinates", new JsonArray().add(entry2.getValue().getKey()).add(entry2.getValue().getValue()));

        }


        obj.add("iridium", "");
        obj.add("created_at", fmt.format(new Date()));
        obj.add("updated_at", fmt.format(new Date()));

        System.out.println(obj);

        URL url = new URL(ripplesUrl);
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        httpCon.setDoOutput(true);
        httpCon.setRequestMethod("PUT");
        httpCon.setRequestProperty("Content-Type", "application/json");
        OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream());
        obj.writeTo(out);
        out.close();
        httpCon.getInputStream();
        System.out.println("Response: "+httpCon.getResponseMessage());

    }





}

