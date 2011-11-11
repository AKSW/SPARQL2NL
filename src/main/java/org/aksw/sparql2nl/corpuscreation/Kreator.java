package org.aksw.sparql2nl.corpuscreation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Set;

import org.dllearner.algorithm.tbsl.sparql.Template;
import org.dllearner.algorithm.tbsl.templator.Templator;


public class Kreator {

	public static String[] FILES = {"resources/TREC_questions_2006.txt","resources/TREC_questions_2007.txt"};
	public static String[] LEXICON = {"resources/english.lex"};
	public static String TARGET = "target/corpus";
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

		Templator temp = new Templator(false); // Templator without WordNet and VERBOSE = false
		temp.setGrammarFiles(LEXICON);
		
		Set<Template> templates;
		
		BufferedReader in;
		BufferedWriter out;
		
		for (String f : FILES) {
			try {
				in = new BufferedReader(new FileReader(f));
				out = new BufferedWriter(new FileWriter("target/parsetest.txt"));
				
				String line;
				while ((line = in.readLine()) != null && !line.startsWith("//")) {
					templates = temp.buildTemplates(line);
					
					// PARSE TEST 
					if (!templates.isEmpty()) {
						System.out.println("["+templates.size()+"] " + line);
						out.write("["+templates.size()+"] " + line);
					}
					else {
						System.out.println("[-] " + line);
						out.write("[-] " + line);
					}
					//
				}
				in.close();
				out.close();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
