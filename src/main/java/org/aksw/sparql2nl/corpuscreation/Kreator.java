package org.aksw.sparql2nl.corpuscreation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Set;

import org.dllearner.algorithm.tbsl.sparql.Template;
import org.dllearner.algorithm.tbsl.templator.Templator;


public class Kreator {

	public static String[] FILES = {"resources/TREC_questions_2006.txt","resources/TREC_questions_2007.txt","resources/TREC_questions_2008.txt","resources/TREC_questions_2009.txt"};
	public static String[] LEXICON = {"resources/english.lex"};
	public static String TARGET = "target/corpus";
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

		Templator temp = new Templator(false); // Templator without WordNet and VERBOSE = false
		temp.setGrammarFiles(LEXICON);
		
		Set<Template> templates;
		
		BufferedReader in;
		BufferedWriter out;
		
		int total = 0;
		int parsed = 0;
		
		try {
			out = new BufferedWriter(new FileWriter("target/parsetest.txt"));
				
			for (String f : FILES) {
				in = new BufferedReader(new FileReader(f));
				String line;
				while ((line = in.readLine()) != null && !line.startsWith("//")) {
					
					try {
						templates = temp.buildTemplates(line);						
						total++;
						
						// PARSE TEST 
						if (!templates.isEmpty()) {
							System.out.println("["+templates.size()+"] " + line);
							out.write("\n["+templates.size()+"] " + line);
							parsed++;
						}
						else {
							System.out.println("[-] " + line);
							out.write("\n[-] " + line);
						}
						//
					}
					catch (OutOfMemoryError e) {
						continue;
					}
					catch (NullPointerException e) {
						continue;
					}
				}
				in.close();
			}
			System.out.println("\n\nTotal: " + total + "\nParsed: " + parsed);
			out.write("\n\nTotal: " + total + "\nParsed: " + parsed);
			out.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

}
