
/*
 * Below is the copyright agreement for this code.
 * 
 * Copyright (c) 2018, Laboratório de Sistemas e Tecnologia Subaquática
 * https://www.lsts.pt
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     - Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     - Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     - Neither the names of IMC, LSTS, IMCJava nor the names of its 
 *       contributors may be used to endorse or promote products derived from 
 *       this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL LABORATORIO DE SISTEMAS E TECNOLOGIA SUBAQUATICA
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE 
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT 
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT 
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *  
 * author: paulo.sousa.dias@gmail.com
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.apache.commons.math3.util.Pair;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

public class Positions {

	private static int imcid = 0;;
	private static final LinkedHashMap<String, String> sensorClass = new LinkedHashMap<>();
	private static String sensorClass2;
	private static HashMap<String, String> typeRqstLst = new LinkedHashMap<>();
	private static LinkedHashMap<String, Double> lastLat = new LinkedHashMap<>();
	private static LinkedHashMap<String, Double> lastLon = new LinkedHashMap<>();
	private static LinkedHashMap<String, Double> penumLat = new LinkedHashMap<>();
	private static LinkedHashMap<String, Double> penumLon = new LinkedHashMap<>();

	private static void get() throws IOException {

		boolean fetchAUVType = true;

		boolean fetchGliderType = true;

		boolean fetchDrifterType = true;

		boolean fetchShipType = true;

		boolean fetchAISType = true;
		final String USER_AGENT = "Mozilla/5.0";

		if (fetchAUVType) {
			typeRqstLst.put("auv/", "AUV");
		}
		if (fetchGliderType) {
			typeRqstLst.put("glider/", "GLIDER");
		}
		if (fetchDrifterType) {
			typeRqstLst.put("drifter/", "DRIFTER");
		}
		if (fetchShipType) {
			typeRqstLst.put("ship/", "SHIP");
		}
		if (fetchAISType) {
			typeRqstLst.put("uav/", "AIS");
		}

		for (String typeRqst : typeRqstLst.keySet()) {

			String url = "http://odss.mbari.org/trackingdb/" + "positionOfType/" + typeRqst + "last/" + 72
					+ "h/data.html";

			sensorClass2 = typeRqst;

			URL obj = new URL(url);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();

			// optional default is GET
			con.setRequestMethod("GET");

			// add request header
			con.setRequestProperty("User-Agent", USER_AGENT);

			int responseCode = con.getResponseCode();
			System.out.println("\nSending 'GET' request to URL : " + url);
			System.out.println("Response Code : " + responseCode);

			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuilder response = new StringBuilder();

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
		String previous = null;
		String current;

		final LinkedHashMap<String, Integer> data = new LinkedHashMap<>();
		org.jsoup.nodes.Document doc = Jsoup.parse(resp);

		Elements links = doc.select("td");
		if (links.size() > 5) {

			for (int i = 0; i < links.size(); i = i + 5) {

				if (i == 0) {
					previous = String.valueOf(links.get(i));
					previous = previous.substring(previous.indexOf(">") + 1, previous.indexOf("/") - 1);

				}

				current = String.valueOf(links.get(i));
				current = current.substring(current.indexOf(">") + 1, current.indexOf("/") - 1);

				if (!previous.equals(current)) {

					longitude = links.get(i - 3).toString();
					longitude = longitude.replaceAll("[^\\.0123456789-]", "");
					double lon = Double.parseDouble(longitude);
					latitude = links.get(i - 2).toString();
					latitude = latitude.replaceAll("[^\\.0123456789-]", "");
					double lat = Double.parseDouble(latitude);

					penumLon.put(previous, lon);
					penumLat.put(previous, lat);
					lastLon.put(previous, lon);
					lastLat.put(previous, lat);

					vehicleData.put(previous, new Pair<>(lat, lon));

				}
				if (links.get(i + 4).equals(links.last())) {
					// se este é o ultimo link

					penumLon.put(current,
							Double.parseDouble(links.get(i - 3).toString().replaceAll("[^\\.0123456789-]", "")));
					penumLat.put(current,
							Double.parseDouble(links.get(i - 2).toString().replaceAll("[^\\.0123456789-]", "")));

					longitude = links.get(i + 2).toString();
					longitude = longitude.replaceAll("[^\\.0123456789-]", "");
					double lon = Double.parseDouble(longitude);

					latitude = links.get(i + 3).toString();
					latitude = latitude.replaceAll("[^\\.0123456789-]", "");
					double lat = Double.parseDouble(latitude);
					lastLon.put(current, lon);
					lastLat.put(current, lat);
					vehicleData.put(current, new Pair<>(lat, lon));

				}
				previous = current;

				synchronized (data) {
					data.put(current, ++imcid);
				}
				synchronized (sensorClass) {
					sensorClass.put(current, String.valueOf(sensorClass2));
				}
			}

			publishToRipples(vehicleData, data);
		}
	}

	private static void publishToRipples(LinkedHashMap<String, Pair<Double, Double>> vehicleData,
			LinkedHashMap<String, Integer> data) throws IOException {
		String ripplesUrl = "http://ripples.lsts.pt/api/v1/systems";
		SimpleDateFormat fmt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ssZ");

		for (String s : data.keySet()) {
			new Thread(() -> {
				JsonObject obj = new JsonObject();
				obj.add("imcid", data.get(s));
				obj.add("name", s);
				obj.add("coordinates",
						new JsonArray().add(vehicleData.get(s).getKey()).add(vehicleData.get(s).getValue()));
				obj.add("iridium", "");
				obj.add("created_at", fmt.format(new Date()));
				obj.add("updated_at", fmt.format(new Date()));

				URL url = null;
				try {
					url = new URL(ripplesUrl);
				}
				catch (MalformedURLException e) {
					e.printStackTrace();
				}
				HttpURLConnection httpCon = null;
				try {
					httpCon = (HttpURLConnection) url.openConnection();

				} catch (IOException e) {
					e.printStackTrace();
				}
				httpCon.setDoOutput(true);
				try {
					httpCon.setRequestMethod("PUT");
				}
				catch (ProtocolException e) {
					e.printStackTrace();
				}
				httpCon.setRequestProperty("Content-Type", "application/json");
				OutputStreamWriter out = null;

				try {
					out = new OutputStreamWriter(httpCon.getOutputStream());
				}
				catch (IOException e) {
					e.printStackTrace();
				}
				try {
					obj.writeTo(out);
				} catch (IOException e) {
					e.printStackTrace();
				}
				try {
					out.close();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
				try {
					httpCon.getInputStream();
				} 
				catch (IOException e) {
					e.printStackTrace();
				}
				try {
					System.out.println("Response: " + httpCon.getResponseMessage());
				}
				catch (IOException e) {
					e.printStackTrace();
				}
				System.out.println(obj);

			}).start();
		}
	}

	public static void main(String[] args) {
		while (true) {
			try {
				get();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
			try {
				Thread.sleep(100000);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

}
