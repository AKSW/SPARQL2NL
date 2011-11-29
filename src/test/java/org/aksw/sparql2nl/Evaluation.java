package org.aksw.sparql2nl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.aksw.sparql2nl.queryprocessing.Query;
import org.aksw.sparql2nl.queryprocessing.Similarity.SimilarityMeasure;
import org.apache.commons.codec.StringEncoder;
import org.apache.commons.lang3.StringEscapeUtils;

import com.hp.hpl.jena.query.QueryFactory;

import edu.stanford.nlp.io.EncodingPrintWriter.out;

public class Evaluation {
	
	private static final String QUERIES_FILE = "resources/queries.txt";
	private static final int NR_OF_REPRESENTATIONS = 10;
	
	private List<String> readQueries() {
		List<String> queries = new ArrayList<String>();

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
					com.hp.hpl.jena.query.Query q = QueryFactory.create(line.substring(1, line.length()-1));
					expandPrefixes(q);
					queries.add(q.toString());
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
	
	private void createLSQFile(String sparqlQuery, List<String> nlRepresentations){
		StringBuilder sb = new StringBuilder();
		sb.append("<document>" +
				"<LimeSurveyDocType>Question</LimeSurveyDocType>" +
				"<DBVersion>145</DBVersion>" +
				"<languages><language>en</language></languages>" +
				"<questions>" +
				"<fields>" +
				"<fieldname>qid</fieldname>" +
				"<fieldname>parent_qid</fieldname>" +
				"<fieldname>sid</fieldname><fieldname>gid</fieldname>" +
				"<fieldname>type</fieldname>" +
				"<fieldname>title</fieldname>" +
				"<fieldname>question</fieldname>" +
				"<fieldname>preg</fieldname>" +
				"<fieldname>help</fieldname>" +
				"<fieldname>other</fieldname>" +
				"<fieldname>mandatory</fieldname>" +
				"<fieldname>question_order</fieldname>" +
				"<fieldname>language</fieldname>" +
				"<fieldname>scale_id</fieldname>" +
				"<fieldname>same_default</fieldname>" +
				"</fields>" +
				"<rows>" +
				"<row>" +
				"<qid>33</qid>" +
				"<parent_qid>0</parent_qid>" +
				"<sid>11714</sid>" +
				"<gid>9</gid>" +
				"<type>B</type>" +
				"<title>q1</title>" +
				"<question>" +
				"<![CDATA[<pre class=\"query\">");
		sb.append(StringEscapeUtils.escapeHtml4(sparqlQuery));
		/*try {
			sb.append(URLEncoder.encode(sparqlQuery, "UTF-8"));
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}*/
		sb.append("</pre>]]>" +
				"</question>" +
				"<preg></preg>" +
				"<help></help>" +
				"<other>N</other>" +
				"<mandatory>N</mandatory>" +
				"<question_order>0</question_order>" +
				"<language>en</language>" +
				"<scale_id>0</scale_id>" +
				"<same_default>0</same_default>" +
				"</row>" +
				"</rows>" +
				"</questions>" +
				"<subquestions>" +
				"<fields>" +
				"<fieldname>qid</fieldname>" +
				"<fieldname>parent_qid</fieldname>" +
				"<fieldname>sid</fieldname>" +
				"<fieldname>gid</fieldname>" +
				"<fieldname>type</fieldname>" +
				"<fieldname>title</fieldname>" +
				"<fieldname>question</fieldname>" +
				"<fieldname>preg</fieldname>" +
				"<fieldname>help</fieldname>" +
				"<fieldname>other</fieldname>" +
				"<fieldname>mandatory</fieldname>" +
				"<fieldname>question_order</fieldname>" +
				"<fieldname>language</fieldname>" +
				"<fieldname>scale_id</fieldname>" +
				"<fieldname>same_default</fieldname>" +
				"</fields>" +
				"<rows>");
		int qid = 0;
		for(String nlRep : nlRepresentations){
			sb.append("<row>" +
					"<qid>" + qid++ + "</qid>" +
					"<parent_qid>33</parent_qid>" +
					"<sid>11714</sid>" +
					"<gid>9</gid>" +
					"<type>T</type>" +
					"<title>SQ001</title>" +
					"<question>" + nlRep + "</question>" +
					"<preg></preg>" +
					"<help></help>" +
					"<other>N</other>" +
					"<mandatory></mandatory>" +
					"<question_order>" + qid + "</question_order>" +
					"<language>en</language>" +
					"<scale_id>0</scale_id>" +
					"<same_default>0</same_default>" +
					"</row>");
		}
		sb.append("</rows>" +
				"</subquestions>" +
				"<question_attributes>" +
				"<fields>" +
				"<fieldname>qid</fieldname>" +
				"<fieldname>attribute</fieldname>" +
				"<fieldname>value</fieldname>" +
				"</fields>" +
				"<rows>" +
				"<row>" +
				"<qid>33</qid>" +
				"<attribute>answer_width</attribute>" +
				"<value></value>" +
				"</row>" +
				"<row>" +
				"<qid>33</qid>" +
				"<attribute>array_filter</attribute>" +
				"<value></value>" +
				"</row>" +
				"<row>" +
				"<qid>33</qid>" +
				"<attribute>array_filter_exclude</attribute>" +
				"<value></value>" +
				"</row>" +
				"<row>" +
				"<qid>33</qid>" +
				"<attribute>hidden</attribute>" +
				"<value>0</value>" +
				"</row>" +
				"<row>" +
				"<qid>33</qid>" +
				"<attribute>page_break</attribute>" +
				"<value>0</value>" +
				"</row>" +
				"<row>" +
				"<qid>33</qid>" +
				"<attribute>public_statistics</attribute>" +
				"<value>0</value>" +
				"</row>" +
				"<row>" +
				"<qid>33</qid>" +
				"<attribute>random_group</attribute>" +
				"<value></value>" +
				"</row>" +
				"<row>" +
				"<qid>33</qid>" +
				"<attribute>random_order</attribute>" +
				"<value>0</value>" +
				"</row>" +
				"</rows>" +
				"</question_attributes>" +
				"</document>");
		
		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new FileWriter("query.lsq"));
			out.write(sb.toString());
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				out.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public void run(){
		List<String> queries = readQueries();
		SPARQL2NL nlGen = new SPARQL2NL();
		nlGen.setMeasure(SimilarityMeasure.GRAPH_ISOMORPHY);
		for(String query : queries){
			System.out.println(query);
			Set<String> nlRepresentations = nlGen.getNaturalLanguageRepresentations(query, NR_OF_REPRESENTATIONS);
			createLSQFile(query, new ArrayList<String>(nlRepresentations));
			
			break;
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new Evaluation().run();
	}

}
