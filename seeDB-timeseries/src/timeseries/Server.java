package timeseries;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;

public class Server {

	@SuppressWarnings("restriction")
	public static void main(String[] args) throws Exception {
		@SuppressWarnings("restriction")
		HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);
		server.createContext("/getResult", new ResultHandler());
		server.setExecutor(null); // creates a default executor
		server.start();
	}

	static class ResultHandler implements HttpHandler {
		@SuppressWarnings("restriction")
		public void handle(HttpExchange t) throws IOException {
			// Parse user inputs (hashtag, start time, end time)
			Map<String, String> params = queryToMap(t.getRequestURI().getQuery());
			LinkedHashMap<String, HashMap<Timestamp, Double>> results = getResults(params);
			System.out.println(t.getResponseHeaders());
			Headers h = t.getResponseHeaders();
			// these are the user inputs
			System.out.println(params);
			h.add("Content-Type", "application/json");
			Gson gson = new Gson();
			String json = gson.toJson(results);
			System.out.println("json" + json);
			t.sendResponseHeaders(200, json.length());
			OutputStream os = t.getResponseBody();
			os.write(json.getBytes());
			os.close();
		}

		// this function is from:
		// http://www.rgagnon.com/javadetails/java-get-url-parameters-using-jdk-http-server.html
		private static Map<String, String> queryToMap(String query) {
			Map<String, String> result = new HashMap<String, String>();
			for (String param : query.split("&")) {
				String pair[] = param.split("=");
				if (pair.length > 1) {
					result.put(pair[0], pair[1]);
				} else {
					result.put(pair[0], "");
				}
			}
			return result;
		}

		// function to get results from seedb backend
		private LinkedHashMap<String, HashMap<Timestamp, Double>> getResults(Map<String, String> params) {
			DBSetting s = DBSetting.getDefault();

			SeeDB seedb = new SeeDB();
			seedb.connectToDatabase(s.database, s.databaseType, s.username, s.password);
			System.out.println("connected to db");
			// convert user input to Timestamps
			
			String userStartTime = params.get("start_time");
			String userEndTime = params.get("end_time");
			Utils u = new Utils();
			Timestamp startTime = u.convertUserTime(userStartTime);
			Timestamp endTime = u.convertUserTime(userEndTime);

			BinningRules binRules = new BinningRules(startTime, endTime);
			String ruling = binRules.determineBinGranularity();
//			// first figure out how you should bin the data
//			// and then call computeCorrelation with the start and end time, with the right binned data
			ruling = "hour";
			if (ruling == "hour") {
				seedb.binTimeData("hashtags", "hashtags_by_hour_window", true);
			} else if (ruling == "min") {
				seedb.binTimeData("hashtags", "hashtags_by_min_window", false);
			}
			
			String target = "job";
//			String[] candidates = { "jobs", "kca", "tweetmyjobs", "vote1duk" };
			String[] candidates = seedb.getPopularHashtags(10);

			// need to convert the start_time to a timestamp...
			// and automatically set the date
			System.out.println("Startig to compute correlation window");
			seedb.computeCorrelationTimeWindow(target, candidates, startTime, endTime);
			System.out.println("Finished compute correlation window");
			LinkedHashMap<String, HashMap<Timestamp, Double>> results = (LinkedHashMap<String, HashMap<Timestamp, Double>>) seedb
					.getHighlyCorrelated(5);
//			results.forEach((k, v) -> System.out.println(k + "=" + v));
			System.out.println("results" + results);
			return results;
		}

	}

}