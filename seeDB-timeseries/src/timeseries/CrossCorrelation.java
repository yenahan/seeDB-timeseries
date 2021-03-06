package timeseries;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

public class CrossCorrelation {
	DBConnection conn;
	
	String target;
	String[] candidates;
	boolean usePreBinned;
	
	HashMap<Timestamp, Double> targetData;
	double[] targetDataArray;
	HashMap<String, HashMap<Timestamp, Double>> candData;
	double[] candDataArray;
	LinkedHashMap<String, Double> candCorrelation;
	
	public CrossCorrelation (DBConnection conn, String target, String binnedData) {
		this.conn = conn;
		this.target = target;
		this.targetData = new HashMap<Timestamp, Double> ();
		this.candData = new HashMap<String, HashMap<Timestamp, Double>> ();
		this.candCorrelation = new LinkedHashMap<String, Double> ();
		this.usePreBinned = true;
	}
	
	public CrossCorrelation (DBConnection conn, String target) {
		this.conn = conn;
		this.target = target;
		this.targetData = new HashMap<Timestamp, Double> ();
		this.candData = new HashMap<String, HashMap<Timestamp, Double>> ();
		this.candCorrelation = new LinkedHashMap<String, Double> ();
		this.usePreBinned = false;
	}
	
	public void computeCrossCorrelationTimeWindow (String target, Timestamp startTime, Timestamp endTime, String binnedData) {
		this.target = target;
		this.targetData = new HashMap<Timestamp, Double> ();
		this.candData = new HashMap<String, HashMap<Timestamp, Double>> ();
		this.candCorrelation = new LinkedHashMap<String, Double> ();
		
		ArrayList<Timestamp> timestamps = new ArrayList<Timestamp> ();
		
		String query = "SELECT * FROM generate_series(\'" + startTime.toString() + "\'::timestamp, \'" + endTime.toString() 
				+ "\', \'1 hour\');";
		System.out.println(query);
		ResultSet rs = conn.executeQuery(query);
		try {
			while (rs.next()) {
				Timestamp t = rs.getTimestamp(1);
				this.targetData.put(t, 0.0);
				timestamps.add(t);
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		// collect target data
		query = "SELECT * FROM " + binnedData + " WHERE hashtag = \'" + target + "\' "
				+ "AND hour >= \'" + startTime.toString() + "\' AND hour <= \'" + endTime.toString() + "\';";
		System.out.println(query);
		rs = conn.executeQuery(query);
		try {
			while (rs.next()) {
				Timestamp t = rs.getTimestamp(1);
				//String hashtag = rs.getString(2);
				double count = (double) rs.getInt(3);
				targetData.put(t, count);
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		targetDataArray = targetData.values().stream().mapToDouble(i->i).toArray();
		
		// collect candidate data
		// query all distinct hashtags (not just appearing within the time window)
		query = "SELECT DISTINCT hashtag FROM hashtags WHERE timestamp >= \'" + startTime.toString() + "\' AND timestamp <= \'" + endTime.toString() + "\';";
		rs = conn.executeQuery(query);
		try {
			while (rs.next()) {
				String candidate = rs.getString(1);
				
				//String query2 =  "SELECT * FROM " + binnedData + ";";
				String query2 =  "SELECT * FROM " + binnedData + " where hashtag = \'" + candidate + "\' "
						+ "AND hour >= \'" + startTime.toString() + "\' AND hour <= \'" + endTime.toString() + "\';";
				//System.out.println(query2);
				ResultSet rs2 = conn.executeQuery(query2);

				HashMap<Timestamp, Double> temp = new HashMap<Timestamp, Double> ();
				for (Timestamp t: timestamps) {
					temp.put(t,  0.0);
				}
		
				while (rs2.next()) {
					Timestamp t = rs2.getTimestamp(1);
					double count = (double) rs2.getInt(3);
					temp.put(t, count);
				}
				candData.put(candidate, temp);
				rs2.close();
				//candDataArray = targetData.values().toArray(candDataArray);
				candDataArray = candData.get(candidate).values().stream().mapToDouble(i->i).toArray();
				
				double coeff = new PearsonsCorrelation().correlation(targetDataArray, candDataArray);
				candCorrelation.put(candidate, coeff);
			}
			rs.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		candCorrelation = 
			     candCorrelation.entrySet().stream()
			    .sorted(Entry.comparingByValue(Comparator.reverseOrder()))
			    .collect(Collectors.toMap(Entry::getKey, Entry::getValue,
			                              (e1, e2) -> e1, LinkedHashMap::new));
		//System.out.println(candCorrelation.toString());
	}
	
	public void computeCrossCorrelationTimeWindow (String target, String[] candidates, Timestamp startTime, Timestamp endTime, String binnedData, boolean isHour) {
		this.target = target;
		this.targetData = new HashMap<Timestamp, Double> ();
		this.candData = new HashMap<String, HashMap<Timestamp, Double>> ();
		this.candCorrelation = new LinkedHashMap<String, Double> ();
		
		ArrayList<Timestamp> timestamps = new ArrayList<Timestamp> ();
		
		String query = "";
		if (isHour) {
			query = "SELECT * FROM generate_series(\'" + startTime.toString() + "\'::timestamp, \'" + endTime.toString() 
				+ "\', \'1 hour\');";
		} else {
			query = "SELECT * FROM generate_series(\'" + startTime.toString() + "\'::timestamp, \'" + endTime.toString() 
			+ "\', \'1 min\');";
		}
		ResultSet rs = conn.executeQuery(query);
		try {
			while (rs.next()) {
				Timestamp t = rs.getTimestamp(1);
				this.targetData.put(t, 0.0);
				timestamps.add(t);
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		// collect target data
		query = "SELECT * FROM " + binnedData + " WHERE hashtag = \'" + target + "\';";
		rs = conn.executeQuery(query);
		try {
			while (rs.next()) {
				Timestamp t = rs.getTimestamp(1);
				//String hashtag = rs.getString(2);
				double count = (double) rs.getInt(3);
				targetData.put(t, count);
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		targetDataArray = targetData.values().stream().mapToDouble(i->i).toArray();
		
		// collect candidate data
		// query all distinct hashtags (not just appearing within the time window)
		
		for (String candidate: candidates) {
			try {	
					String query2 =  "SELECT * FROM " + binnedData + " where hashtag = \'" + candidate + "\';";
					ResultSet rs2 = conn.executeQuery(query2);
					
					if (rs2.isBeforeFirst()) { 
						HashMap<Timestamp, Double> temp = new HashMap<Timestamp, Double> ();
						for (Timestamp t: timestamps) {
							temp.put(t,  0.0);
						}
						while (rs2.next()) {
							Timestamp t = rs2.getTimestamp(1);
							double count = (double) rs2.getInt(3);
							temp.put(t, count);
						}
						candData.put(candidate, temp);
						rs2.close();
						//candDataArray = targetData.values().toArray(candDataArray);
						candDataArray = candData.get(candidate).values().stream().mapToDouble(i->i).toArray();
						
						double coeff = new PearsonsCorrelation().correlation(targetDataArray, candDataArray);
						candCorrelation.put(candidate, coeff);
					}
			}
			catch (SQLException e) {
				e.printStackTrace();
			}

		} 
		
		candCorrelation = 
			     candCorrelation.entrySet().stream()
			    .sorted(Entry.comparingByValue(Comparator.reverseOrder()))
			    .collect(Collectors.toMap(Entry::getKey, Entry::getValue,
			                              (e1, e2) -> e1, LinkedHashMap::new));
		//System.out.println(candCorrelation.toString());
	}
	
	public void computeCrossCorrelationNormalized (String target, String binnedData) {
		this.target = target;
		this.targetData = new HashMap<Timestamp, Double> ();
		this.candData = new HashMap<String, HashMap<Timestamp, Double>> ();
		this.candCorrelation = new LinkedHashMap<String, Double> ();
		
		// initialize count to 0
		for (int i = 0; i < 24; i ++) {
		//for (int i = 8; i < 24; i ++) {
			String hour = "" + i;
			if (i < 10) hour = "0" + hour;
			this.targetData.put(Timestamp.valueOf("2015-02-24 " + hour + ":00:00.0"), 0.0);
		}
		
		// collect target data
		String query = "SELECT * FROM " + binnedData + " WHERE hashtag = \'" + target + "\';";
		ResultSet rs = conn.executeQuery(query);
		try {
			while (rs.next()) {
				Timestamp t = rs.getTimestamp(1);
				//String hashtag = rs.getString(2);
				double count = (double) rs.getInt(3);
				targetData.put(t, count);
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		targetDataArray = targetData.values().stream().mapToDouble(i->i).toArray();
		
		// collect candidate data 
		query = "SELECT DISTINCT hashtag FROM hashtags;";
		rs = conn.executeQuery(query);
		try {
			while (rs.next()) {
				String candidate = rs.getString(1);
				
				String query2 =  "SELECT * FROM " + binnedData + " where hashtag = \'" + candidate + "\';";
				ResultSet rs2 = conn.executeQuery(query2);
				HashMap<Timestamp, Double> temp = new HashMap<Timestamp, Double> ();
				for (int i = 0; i < 24; i ++) {
				//for (int i = 8; i < 24; i ++) {
					String hour = "" + i;
					if (i < 10) hour = "0" + hour;
					temp.put(Timestamp.valueOf("2015-02-24 " + hour + ":00:00.0"), 0.0);
				}
		
				while (rs2.next()) {
					Timestamp t = rs2.getTimestamp(1);
					double count = (double) rs2.getInt(3);
					temp.put(t, count);
				}
				candData.put(candidate, temp);
				rs2.close();
				//candDataArray = targetData.values().toArray(candDataArray);
				candDataArray = candData.get(candidate).values().stream().mapToDouble(i->i).toArray();
				
				double coeff = new PearsonsCorrelation().correlation(StatUtils.normalize(targetDataArray), StatUtils.normalize(candDataArray));
				if (candidate.equals(target)) {
					System.out.println(Arrays.toString(targetDataArray));
					System.out.println(Arrays.toString(candDataArray));
					System.out.println(coeff);
				}
				
				candCorrelation.put(candidate, coeff);
			}
			rs.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		candCorrelation = 
			     candCorrelation.entrySet().stream()
			    .sorted(Entry.comparingByValue(Comparator.reverseOrder()))
			    .collect(Collectors.toMap(Entry::getKey, Entry::getValue,
			                              (e1, e2) -> e1, LinkedHashMap::new));
		System.out.println(candCorrelation.toString());

	}
	
	
	public void computeCrossCorrelation (String target, String binnedData) {
		this.target = target;
		this.targetData = new HashMap<Timestamp, Double> ();
		this.candData = new HashMap<String, HashMap<Timestamp, Double>> ();
		this.candCorrelation = new LinkedHashMap<String, Double> ();
		
		// initialize count to 0
		for (int i = 0; i < 24; i ++) {
		//for (int i = 8; i < 24; i ++) {
			String hour = "" + i;
			if (i < 10) hour = "0" + hour;
			this.targetData.put(Timestamp.valueOf("2015-02-24 " + hour + ":00:00.0"), 0.0);
		}
		
		// collect target data
		String query = "SELECT * FROM " + binnedData + " WHERE hashtag = \'" + target + "\';";
		ResultSet rs = conn.executeQuery(query);
		try {
			while (rs.next()) {
				Timestamp t = rs.getTimestamp(1);
				//String hashtag = rs.getString(2);
				double count = (double) rs.getInt(3);
				targetData.put(t, count);
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		targetDataArray = targetData.values().stream().mapToDouble(i->i).toArray();
		
		// collect candidate data 
		query = "SELECT DISTINCT hashtag FROM hashtags;";
		rs = conn.executeQuery(query);
		try {
			while (rs.next()) {
				String candidate = rs.getString(1);
				
				String query2 =  "SELECT * FROM " + binnedData + " where hashtag = \'" + candidate + "\';";
				ResultSet rs2 = conn.executeQuery(query2);
				HashMap<Timestamp, Double> temp = new HashMap<Timestamp, Double> ();
				for (int i = 0; i < 24; i ++) {
				//for (int i = 8; i < 24; i ++) {
					String hour = "" + i;
					if (i < 10) hour = "0" + hour;
					temp.put(Timestamp.valueOf("2015-02-24 " + hour + ":00:00.0"), 0.0);
				}
		
				while (rs2.next()) {
					Timestamp t = rs2.getTimestamp(1);
					double count = (double) rs2.getInt(3);
					temp.put(t, count);
				}
				candData.put(candidate, temp);
				rs2.close();
				//candDataArray = targetData.values().toArray(candDataArray);
				candDataArray = candData.get(candidate).values().stream().mapToDouble(i->i).toArray();
				
				double coeff = new PearsonsCorrelation().correlation(targetDataArray, candDataArray);
				candCorrelation.put(candidate, coeff);
			}
			rs.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		candCorrelation = 
			     candCorrelation.entrySet().stream()
			    .sorted(Entry.comparingByValue(Comparator.reverseOrder()))
			    .collect(Collectors.toMap(Entry::getKey, Entry::getValue,
			                              (e1, e2) -> e1, LinkedHashMap::new));
		//System.out.println(candCorrelation.toString());

	}
	/**
	 * Change granularity of binning from min to hour and then compute correlation coefficient
	 * @param target
	 * @param candidates
	 * @param binnedData
	 */
	public void computeCrossCorrelationDiffGranularity (String target, String[] candidates, String binnedData) {
		this.target = target;
		this.candidates = candidates;
		this.targetData = new HashMap<Timestamp, Double> ();
		this.candData = new HashMap<String, HashMap<Timestamp, Double>> ();
		this.candCorrelation = new LinkedHashMap<String, Double> ();
		
		//binned by hour
		for (int i = 0; i < 24; i ++) {
			//for (int i = 8; i < 24; i ++) {
				String hour = "" + i;
				if (i < 10) hour = "0" + hour;
				this.targetData.put(Timestamp.valueOf("2015-02-24 " + hour + ":00:00.0"), 0.0);
		}
		
		String query = "SELECT  date_trunc(\'hour\', \"min\"), sum(cnt) FROM " + binnedData 
				+ " WHERE hashtag = \'" + target + "\' GROUP BY  date_trunc(\'hour\', \"min\");";	
		
		ResultSet rs = conn.executeQuery(query);
		try {
			while (rs.next()) {
				Timestamp t = rs.getTimestamp(1);
				//String hashtag = rs.getString(2);
				double count = (double) rs.getInt(2);
				targetData.put(t, count);
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		//targetDataArray = targetData.values().toArray(targetDataArray);
		targetDataArray = targetData.values().stream().mapToDouble(i->i).toArray();
		
		//System.out.println(target + Arrays.toString(targetDataArray));
				
		for (String candidate: candidates) {
			String query2 = "SELECT  date_trunc(\'hour\', \"min\"), sum(cnt) FROM " + binnedData 
					+ " WHERE hashtag = \'" + candidate + "\' GROUP BY  date_trunc(\'hour\', \"min\");";	
			rs = conn.executeQuery(query2);
			HashMap<Timestamp, Double> temp = new HashMap<Timestamp, Double> ();
			
			// binned by hour
			for (int i = 0; i < 24; i ++) {
				//for (int i = 8; i < 24; i ++) {
					String hour = "" + i;
					if (i < 10) hour = "0" + hour;
					temp.put(Timestamp.valueOf("2015-02-24 " + hour + ":00:00.0"), 0.0);
			}
			try {
				while (rs.next()) {
					Timestamp t = rs.getTimestamp(1);
					double count = (double) rs.getInt(2);
					temp.put(t, count);
				}
				candData.put(candidate, temp);
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			//candDataArray = targetData.values().toArray(candDataArray);
			candDataArray = candData.get(candidate).values().stream().mapToDouble(i->i).toArray();
			
			//System.out.println(candidate + Arrays.toString(candDataArray));
			
			double coeff = new PearsonsCorrelation().correlation(targetDataArray, candDataArray);
			candCorrelation.put(candidate, coeff);
			
		}
				
		candCorrelation = 
			     candCorrelation.entrySet().stream()
			    .sorted(Entry.comparingByValue(Comparator.reverseOrder()))
			    .collect(Collectors.toMap(Entry::getKey, Entry::getValue,
			                              (e1, e2) -> e1, LinkedHashMap::new));
		//System.out.println(candCorrelation.toString());
		
	}
	
	public void computeCrossCorrelation (String target, String[] candidates, String binnedData) {
		this.target = target;
		this.candidates = candidates;
		this.targetData = new HashMap<Timestamp, Double> ();
		this.candData = new HashMap<String, HashMap<Timestamp, Double>> ();
		this.candCorrelation = new LinkedHashMap<String, Double> ();
		
		for (int i = 0; i < 24; i ++) {
			for (int j = 0; j < 60; j++) {
				String hour = "" + i;
				if (i < 10) hour = "0" + hour;
				String min = "" + j;
				if (j < 10) min = "0" + min;
				this.targetData.put(Timestamp.valueOf("2015-02-24 " + hour + ":" + min + ":00.0"), 0.0);
			}
		}
		
		/*
		//binned by hour
		for (int i = 0; i < 24; i ++) {
			//for (int i = 8; i < 24; i ++) {
				String hour = "" + i;
				if (i < 10) hour = "0" + hour;
				this.targetData.put(Timestamp.valueOf("2015-02-24 " + hour + ":00:00.0"), 0.0);
		}*/
	
		
		String query = "SELECT * FROM " + binnedData + " WHERE hashtag = \'" + target + "\';";
		ResultSet rs = conn.executeQuery(query);
		try {
			while (rs.next()) {
				Timestamp t = rs.getTimestamp(1);
				//String hashtag = rs.getString(2);
				double count = (double) rs.getInt(3);
				targetData.put(t, count);
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		//targetDataArray = targetData.values().toArray(targetDataArray);
		targetDataArray = targetData.values().stream().mapToDouble(i->i).toArray();
		
		//System.out.println(target + Arrays.toString(targetDataArray));
				
		for (String candidate: candidates) {
			String query2 =  "SELECT * FROM " + binnedData + " where hashtag = \'" + candidate + "\';";
			rs = conn.executeQuery(query2);
			HashMap<Timestamp, Double> temp = new HashMap<Timestamp, Double> ();
			
			for (int i = 0; i < 24; i ++) {
				for (int j = 0; j < 60; j++) {
					String hour = "" + i;
					if (i < 10) hour = "0" + hour;
					String min = "" + j;
					if (j < 10) min = "0" + min;
					temp.put(Timestamp.valueOf("2015-02-24 " + hour + ":" + min + ":00.0"), 0.0);
				}
			}
			
			/*
			// binned by hour
			for (int i = 0; i < 24; i ++) {
				//for (int i = 8; i < 24; i ++) {
					String hour = "" + i;
					if (i < 10) hour = "0" + hour;
					temp.put(Timestamp.valueOf("2015-02-24 " + hour + ":00:00.0"), 0.0);
			}*/
			try {
				while (rs.next()) {
					Timestamp t = rs.getTimestamp(1);
					double count = (double) rs.getInt(3);
					temp.put(t, count);
				}
				candData.put(candidate, temp);
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			//candDataArray = targetData.values().toArray(candDataArray);
			candDataArray = candData.get(candidate).values().stream().mapToDouble(i->i).toArray();
			
			//System.out.println(candidate + Arrays.toString(candDataArray));
			
			double coeff = new PearsonsCorrelation().correlation(targetDataArray, candDataArray);
			candCorrelation.put(candidate, coeff);
			
		}
				
		candCorrelation = 
			     candCorrelation.entrySet().stream()
			    .sorted(Entry.comparingByValue(Comparator.reverseOrder()))
			    .collect(Collectors.toMap(Entry::getKey, Entry::getValue,
			                              (e1, e2) -> e1, LinkedHashMap::new));
		//System.out.println(candCorrelation.toString());
		
	}
	
	public void computeEuclideanDistance (String target, String[] candidates, String binnedData) {
		this.target = target;
		this.candidates = candidates;
		this.targetData = new HashMap<Timestamp, Double> ();
		this.candData = new HashMap<String, HashMap<Timestamp, Double>> ();
		this.candCorrelation = new LinkedHashMap<String, Double> ();
		
		/*for (int i = 0; i < 24; i ++) {
			for (int j = 0; j < 60; j++) {
				String hour = "" + i;
				if (i < 10) hour = "0" + hour;
				String min = "" + j;
				if (j < 10) min = "0" + min;
				this.targetData.put(Timestamp.valueOf("2015-02-24 " + hour + ":" + min + ":00.0"), 0.0);
			}
		}*/
		
		//binned by hour
		for (int i = 0; i < 24; i ++) {
			//for (int i = 8; i < 24; i ++) {
				String hour = "" + i;
				if (i < 10) hour = "0" + hour;
				this.targetData.put(Timestamp.valueOf("2015-02-24 " + hour + ":00:00.0"), 0.0);
		}
	
		
		String query = "SELECT * FROM " + binnedData + " WHERE hashtag = \'" + target + "\';";
		ResultSet rs = conn.executeQuery(query);
		try {
			while (rs.next()) {
				Timestamp t = rs.getTimestamp(1);
				//String hashtag = rs.getString(2);
				double count = (double) rs.getInt(3);
				targetData.put(t, count);
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		//targetDataArray = targetData.values().toArray(targetDataArray);
		targetDataArray = targetData.values().stream().mapToDouble(i->i).toArray();
		
		//System.out.println(target + Arrays.toString(targetDataArray));
				
		for (String candidate: candidates) {
			String query2 =  "SELECT * FROM " + binnedData + " where hashtag = \'" + candidate + "\';";
			rs = conn.executeQuery(query2);
			HashMap<Timestamp, Double> temp = new HashMap<Timestamp, Double> ();
			
			/*for (int i = 0; i < 24; i ++) {
				for (int j = 0; j < 60; j++) {
					String hour = "" + i;
					if (i < 10) hour = "0" + hour;
					String min = "" + j;
					if (j < 10) min = "0" + min;
					temp.put(Timestamp.valueOf("2015-02-24 " + hour + ":" + min + ":00.0"), 0.0);
				}
			}*/
			
			// binned by hour
			for (int i = 0; i < 24; i ++) {
				//for (int i = 8; i < 24; i ++) {
					String hour = "" + i;
					if (i < 10) hour = "0" + hour;
					temp.put(Timestamp.valueOf("2015-02-24 " + hour + ":00:00.0"), 0.0);
			}
			try {
				while (rs.next()) {
					Timestamp t = rs.getTimestamp(1);
					double count = (double) rs.getInt(3);
					temp.put(t, count);
				}
				candData.put(candidate, temp);
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			//candDataArray = targetData.values().toArray(candDataArray);
			candDataArray = candData.get(candidate).values().stream().mapToDouble(i->i).toArray();
			
			//System.out.println(candidate + Arrays.toString(candDataArray));
			
			double coeff = new EuclideanDistance().compute(targetDataArray, candDataArray);
			candCorrelation.put(candidate, coeff);
			
		}
				
		candCorrelation = 
			     candCorrelation.entrySet().stream()
			    .sorted(Entry.comparingByValue())
			    .collect(Collectors.toMap(Entry::getKey, Entry::getValue,
			                              (e1, e2) -> e1, LinkedHashMap::new));
		System.out.println(candCorrelation.toString());
		
	}

	public HashMap<String, HashMap<Timestamp, Double>> getHighlyCorrelated (int n) {
		LinkedHashMap<String, HashMap<Timestamp, Double>>  topCandidateData = new LinkedHashMap<String, HashMap<Timestamp, Double>> ();
		topCandidateData.put(this.target, this.targetData); 
		Iterator<Entry<String, Double>> it = candCorrelation.entrySet().iterator();
		int i = 0;
		while (it.hasNext() && i < n) {
			Map.Entry temp = (Map.Entry) it.next();
			String cand = (String) temp.getKey();
			topCandidateData.put(cand, candData.get(cand));
			i ++;
		}
		return topCandidateData;
	}
}