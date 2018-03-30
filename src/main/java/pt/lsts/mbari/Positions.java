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
package pt.lsts.mbari;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

public class Positions {

	private static String ripplesUrl = "http://ripples.lsts.pt/api/v1/systems";
	
	private static int periodSeconds = 30;
	private static int hoursToFetch = 72;
	
	private static boolean fetchAUVType = true;
	private static boolean fetchGliderType = true;
	private static boolean fetchDrifterType = true;
	private static boolean fetchShipType = true;
	private static boolean fetchAISType = true;
	
	private static String sensorClass;
	private static HashMap<String, String> typeRqstLst = new LinkedHashMap<>();

	private static Map<String, Platform> platformList = Collections.synchronizedMap(new HashMap<String, Platform>());
	private static HashSet<String> validPlatformsNames = new HashSet<>();

	private static class Platform {
		String name;
		long timeMillis = 0;
		double latDeg = 0;
		double lonDeg = 0;
		@SuppressWarnings("unused")
		String type = "unknown";
		
		boolean updated = true;
		
		public Platform(String name, long timeMillis, double latDeg, double lonDeg, String type) {
			this.name = name;
			this.timeMillis = timeMillis;
			this.latDeg = latDeg;
			this.lonDeg = lonDeg;
			this.type = type;
		}
		
		public void update(long timeMillis, double latDeg, double lonDeg) {
			if (timeMillis <= this.timeMillis)
				return;
			
			this.timeMillis = timeMillis;
			this.latDeg = latDeg;
			this.lonDeg = lonDeg;
			this.updated = true;
		}
		
		public boolean isUpdated() {
			return updated;
		}
		
		public void resetUpdateFlag() {
			this.updated = false;
		}
	}
	
	private static void get() throws IOException {
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
			try {
				String url = "http://odss.mbari.org/trackingdb/" + "positionOfType/" + typeRqst + "last/" + hoursToFetch
						+ "h/data.csv";
				
				sensorClass = typeRqst;
				
				URL obj = new URL(url);
				HttpURLConnection con = (HttpURLConnection) obj.openConnection();
				
				// optional default is GET
				con.setRequestMethod("GET");
				
				// add request header
				con.setRequestProperty("User-Agent", USER_AGENT);
				
				int responseCode = con.getResponseCode();
				System.out.println("\nSending 'GET' request to URL : " + url);
				System.out.println("Response Code : " + responseCode);
				
				if (responseCode != 200)
					continue;
				
				BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
				parseCSV(in, sensorClass);
				in.close();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		publishToRipples(platformList);
	}

	private static void parseCSV(BufferedReader in, String sensorClass) {
		try {
			String line = in.readLine(); // first line is the header
			line = in.readLine();
			while (line != null) {
				try {
					String[] tk = line.split(" *?, *?");
					if (tk.length >= 5) {
						String name = tk[0];
						
						if (validPlatformsNames.isEmpty() || validPlatformsNames.contains(name.toLowerCase())) {
							long timeMillis = (long) (Double.parseDouble(tk[1]) * 1E3);
							double lonDeg = Double.parseDouble(tk[2]);
							double latDeg = Double.parseDouble(tk[3]);
							
							Platform pt = platformList.get(name);
							if (pt == null) {
								pt = new Platform(name, timeMillis, latDeg, lonDeg, sensorClass);
								platformList.put(name, pt);
							}
							else {
								pt.update(timeMillis, latDeg, lonDeg);
							}
						}
					}
				}
				catch (Exception e) {
					e.printStackTrace();;
				}
				line = in.readLine();
			}
		}
		catch (Exception e) {
			e.printStackTrace();;
		}
	}

	private static void publishToRipples(Map<String, Platform> platformList) {
		SimpleDateFormat fmt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ssZ");

		platformList.values().stream().forEach(p -> {
			try {
				if (!p.isUpdated())
					return;
				
				JsonObject obj = new JsonObject();
				obj.add("imcid", 0xFFFF);
				obj.add("name", p.name);
				obj.add("coordinates", new JsonArray().add(p.latDeg).add(p.lonDeg));
				obj.add("iridium", "");
				obj.add("created_at", fmt.format(new Date(p.timeMillis)));
				obj.add("updated_at", fmt.format(new Date(p.timeMillis)));

				URL url = null;
				try {
					url = new URL(ripplesUrl);
					HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
					httpCon.setDoOutput(true);
					httpCon.setRequestMethod("PUT");
					httpCon.setRequestProperty("Content-Type", "application/json");

					OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream());
					obj.writeTo(out);
					out.close();

					httpCon.getInputStream();
					System.out.println("Response: " + httpCon.getResponseMessage());
					
					p.resetUpdateFlag();
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				System.out.println(obj);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	public static void main(String[] args) {
		Options options = new Options();
		
		options.addOption(Option.builder("h").required(false).longOpt("help").desc("this help").build());
		
		options.addOption(Option.builder().required(false).longOpt("auv").desc("fetch AUVs").hasArg().argName("true/false")
				.optionalArg(true).type(Boolean.class).build());
		options.addOption(Option.builder().required(false).longOpt("glider").desc("fetch gliders").hasArg()
				.argName("true/false").optionalArg(true).type(Boolean.class).build());
		options.addOption(Option.builder().required(false).longOpt("drifter").desc("fetch drifters").hasArg()
				.argName("true/false").optionalArg(true).type(Boolean.class).build());
		options.addOption(Option.builder().required(false).longOpt("ship").desc("fetch ships").hasArg().argName("true/false")
				.optionalArg(true).type(Boolean.class).build());
		options.addOption(Option.builder().required(false).longOpt("ais").desc("fetch AISs").hasArg().argName("true/false")
				.optionalArg(true).type(Boolean.class).build());

		options.addOption(Option.builder("t").required(false).longOpt("time").desc("hours to fetch").hasArg()
				.argName("HOURS").type(Integer.class).build());
		options.addOption(Option.builder("p").required(false).longOpt("period").desc("period to run the fetch").hasArg()
				.argName("MINUTES").type(Integer.class).build());

		options.addOption(Option.builder().required(false).longOpt("only").desc("only process these names (case insensive)").
				hasArg().argName("name1,name2,...").build());

        HelpFormatter formatter = new HelpFormatter();

		CommandLineParser parser = new DefaultParser();
		CommandLine line = null;
		try {
			line = parser.parse(options, args);

			if(line.hasOption("h")) {
				formatter.printHelp(" ", options);
				System.exit(0);
			}
		}
		catch(ParseException exp) {
			System.err.println("Parsing failed.  Reason: " + exp.getMessage());
			formatter.printHelp(" ", options);
			System.exit(1);
		}

		if(line.hasOption("p")) {
			try {
				int p = Integer.parseInt(line.getOptionValue("p"));
				if (p > 0)
					periodSeconds = p;
			}
			catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}

		if(line.hasOption("t")) {
			try {
				int t = Integer.parseInt(line.getOptionValue("t"));
				if (t > 0)
					hoursToFetch = t;
			}
			catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}

		if(line.hasOption("auv")) {
			try {
				String ar = line.getOptionValue("auv");
				if (ar != null)
					fetchAUVType = Boolean.parseBoolean(ar);
			}
			catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
		if(line.hasOption("glider")) {
			try {
				String ar = line.getOptionValue("glider");
				if (ar != null)
					fetchGliderType = Boolean.parseBoolean(ar);
			}
			catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
		if(line.hasOption("drifter")) {
			try {
				String ar = line.getOptionValue("drifter");
				if (ar != null)
					fetchDrifterType = Boolean.parseBoolean(ar);
			}
			catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
		if(line.hasOption("ship")) {
			try {
				String ar = line.getOptionValue("ship");
				if (ar != null)
					fetchShipType = Boolean.parseBoolean(ar);
			}
			catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
		if(line.hasOption("ais")) {
			try {
				String ar = line.getOptionValue("ais");
				if (ar != null)
					fetchAISType = Boolean.parseBoolean(ar);
			}
			catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}

		if(line.hasOption("only")) {
			try {
				String ar = line.getOptionValue("only");
				if (ar != null) {
					String[] tk = ar.split(" *?, *?");
					for (int i = 0; i < tk.length; i++)
						validPlatformsNames.add(tk[i].toLowerCase());
				}
			}
			catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}

		long periodMillis = periodSeconds * 1000;

		while (true) {
			try {
				get();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
			try {
				Thread.sleep(periodMillis);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
