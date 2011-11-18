package org.aksw.sparql2nl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;

import org.aksw.sparql2nl.queryprocessing.Query;
import org.aksw.sparql2nl.queryprocessing.Similarity;
import org.aksw.sparql2nl.queryprocessing.Similarity.SimilarityMeasure;
import org.dllearner.algorithm.tbsl.sparql.Template;

import com.hp.hpl.jena.query.QueryFactory;

public class SimilarityEvaluationScript {
	
	private static final String TEMPLATES_FILE = "resources/corpus";
	private static final String QUERIES_FILE = "resources/dbpedia_log.csv";
	
	public SimilarityEvaluationScript() {
		// TODO Auto-generated constructor stub
	}
	
	public void run(){
		List<Query> queries = readQueries();
		Hashtable<Template, String> templates = readTemplates();
		
		
		for (Query query1 : queries) {
			for(Template t : templates.keySet()){
				if(t.getQuery().toString().contains("ERROR"))continue;//TODO here is a bug in the tmeplate generation, where some Queries only contain the String 'ERROR'
				Query query2 = new Query(t.getQuery().toString());
				
				double sim = Similarity.getSimilarity(query1, query2, SimilarityMeasure.LEVENHSTEIN);
				System.out.println(sim);
			}
		}
		
	}
	
	private List<Query> readQueries() {
		List<Query> queries = new ArrayList<Query>();

		BufferedReader bufRdr = null;
		try {
			File file = new File(QUERIES_FILE);

			bufRdr = new BufferedReader(new FileReader(file));
			String line = null;

			int i = 0;
			// read each line of text file
			while ((line = bufRdr.readLine()) != null) {
				if(i++ == 10)break;
				System.out.println(line);
				// we use JENA to expand all prefixes
				com.hp.hpl.jena.query.Query q = QueryFactory.create(line);
				expandPrefixes(q);
				queries.add(new Query(q.toString()));
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally{
			if(bufRdr != null){
				try {
					bufRdr.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return queries;
	}
	
	private void expandPrefixes(com.hp.hpl.jena.query.Query query){
		for(Entry<String, String> e : query.getPrefixMapping().getNsPrefixMap().entrySet()){
			query.getPrefixMapping().removeNsPrefix(e.getKey());
		}
	}
	
	private Hashtable<Template, String> readTemplates(){
		try {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(new File(TEMPLATES_FILE)));
			Hashtable<Template, String> templates = (Hashtable<Template, String>)ois.readObject();
			return templates;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new SimilarityEvaluationScript().run();

	}

}
