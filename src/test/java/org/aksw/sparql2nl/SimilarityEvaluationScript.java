package org.aksw.sparql2nl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.aksw.sparql2nl.queryprocessing.Query;
import org.aksw.sparql2nl.queryprocessing.Similarity;
import org.aksw.sparql2nl.queryprocessing.Similarity.SimilarityMeasure;
import org.dllearner.algorithm.tbsl.sparql.Template;
import org.dllearner.algorithm.tbsl.util.LatexWriter;

import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.Syntax;

public class SimilarityEvaluationScript {
	
	private static final String TEMPLATES_FILE = "resources/corpus";
	private static final String QUERIES_FILE = "resources/dbpedia_log.csv";
	
	public SimilarityEvaluationScript() {
		// TODO Auto-generated constructor stub
	}
	
	public void run(){
		List<Query> queries = readQueries();
		run(queries);
		
		
	}
	
	public void run(List<Query> queries){
		Hashtable<Template, String> templates = readTemplates();
		
		
		for(SimilarityMeasure measure : SimilarityMeasure.values()){
			LatexWriter latex = new LatexWriter();
			latex.beginDocument();
			int i = 1;
			for (Query query1 : queries) {if(i==31)break;
				latex.beginSection("", i++);
				latex.beginSubsection("Query");
				latex.addListing(query1.getOriginalQuery());
				latex.addListing(query1.getQueryWithOnlyVars());
				Map<Query, Double> simMap = new HashMap<Query, Double>();
				Map<Query, String> queriesMap = new HashMap<Query, String>();
				int j = 0;
				for(Template t : templates.keySet()){//if(j++ == 10)break;
					//TODO here is a bug in the template generation, where some Queries only contain the String 'ERROR'
					if(t.getQuery().toString().contains("ERROR"))continue;

					com.hp.hpl.jena.query.Query q = null;
					try {
						q = QueryFactory.create(t.getQuery().toString(), Syntax.syntaxARQ);
					} catch (Exception e) {
						e.printStackTrace();
						continue;
					}
					expandPrefixes(q);
					Query query2 = new Query(q.toString());
					
					double sim = Similarity.getSimilarity(query1, query2, measure);
					simMap.put(query2, sim);
					queriesMap.put(query2, templates.get(t));
				}
				latex.beginSubsection("Top 20");
				for(Entry<Query, Double> entry : sortByValues(simMap).subList(0, 20)){
					latex.addListing(entry.getKey().getOriginalQuery() + "\nSimilarity: " + entry.getValue());
					latex.addListing(entry.getKey().getQueryWithOnlyVars());
					latex.addText(queriesMap.get(entry.getKey()));
				}
			}
			latex.endDocument();
			String doc = latex.loadPraeambel();
			doc += "\\subtitle{" + measure.toString().replace("_", "\\_") + "}\n";
			doc += latex.toString();
			try {
				File file = new File("log/" + measure.toString().toLowerCase() + ".tex");
				file.createNewFile();
				Writer output = new BufferedWriter(new FileWriter(file));
				    try {
				      output.write(doc);
				    }
				    finally {
				      output.close();
				    }
			} catch (IOException e) {
				e.printStackTrace();
			}
//			break;
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
//				if(i++ == 10)break;
//				System.out.println(line);
				// we use JENA to expand all prefixes
				try {
					com.hp.hpl.jena.query.Query q = QueryFactory.create(line);
					expandPrefixes(q);
					queries.add(new Query(q.toString()));
				} catch (Exception e) {
					e.printStackTrace();
				}
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
			System.out.println(templates.keySet().size());
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
	
	private <K, V extends Comparable<V>> List<Entry<K, V>> sortByValues(Map<K, V> map){
		List<Entry<K, V>> entries = new ArrayList<Entry<K, V>>(map.entrySet());
        Collections.sort(entries, new Comparator<Entry<K, V>>() {

			@Override
			public int compare(Entry<K, V> o1, Entry<K, V> o2) {
				return o2.getValue().compareTo(o1.getValue());
			}
		});
        return entries;
	}
	

	/**
	 * @param args
	 */
	public static void main(String[] args) {
//		new SimilarityEvaluationScript().run();
		
		String q = "SELECT ?s WHERE {?s rdf:type <http://test.org/City>. ?s rdf:type <http://test.org/Cities>. ?x ?p ?s.}";
		new SimilarityEvaluationScript().run(Collections.singletonList(new Query(q)));

	}

}
