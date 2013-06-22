/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer.rouge;

import com.aliasi.chunk.Chunk;
import com.aliasi.chunk.Chunking;
import com.aliasi.sentences.SentenceChunker;
import com.aliasi.sentences.SentenceModel;
import com.aliasi.sentences.IndoEuropeanSentenceModel;
import com.aliasi.tokenizer.*;
import com.aliasi.util.Streams;
import java.io.*;
import java.util.*;

public class Rouge {

    public static final int ROUGE_N = 1;
    public static final int ROUGE_L = 2;
    public static final int ROUGE_W = 3;
    public static final int ROUGE_S = 4;
    public static final int ROUGE_SU = 5;
    public static final int MULTIPLE_MAX = 1;
    public static final int MULTIPLE_MIN = 2;
    public static final int MULTIPLE_AVG = 3;
    private SentenceChunker mSentenceChunker;
    private TokenizerFactory tokenExtractor;
    private double beta; //control the f-score
    private double[][] evaStat; //store evaluation result
    private int metric; //ROUGE metrics
    private int multipleMode;
    private int gram; //used for ROUGE-N metric
    private int maxSkip; //used for ROUGE-S metric
    private boolean caseSensitive;

    public Rouge() {
        caseSensitive = false;
        tokenExtractor = new IndoEuropeanTokenizerFactory();
        mSentenceChunker = new SentenceChunker(IndoEuropeanTokenizerFactory.INSTANCE, new IndoEuropeanSentenceModel());
        beta = 1.0;
        metric = ROUGE_N;
        gram = 2;
    }

    public void setBeta(double beta) {
        if (beta > 0) {
            this.beta = beta;
        }
    }

    public double getBeta() {
        return beta;
    }

    public void setMultipleReferenceMode(int mode) {
        this.multipleMode = mode;
    }

    public void setCaseOption(boolean sensitive) {
        caseSensitive = sensitive;
    }

    public boolean getCaseOption() {
        return caseSensitive;
    }

    public void useRougeN(int gram) {
        this.gram = gram;
        metric = ROUGE_N;
    }

    public int getGram() {
        return gram;
    }

    public void useRougeS() {
        this.maxSkip = Integer.MAX_VALUE;
        metric = ROUGE_S;
    }

    public void useRougeS(int maxSkip) {
        this.maxSkip = maxSkip;
        metric = ROUGE_S;
    }

    public double getPrecision() {
        return getEvaResult(1);
    }

    public double getRecall() {
        return getEvaResult(0);
    }

    public double getFScore() {
        return getEvaResult(2);
    }

    public static double max(double[] values) {
        double aux = Double.MIN_VALUE;
        for (double val : values) {
            if (val > aux) {
                aux = val;
            }
        }
        return aux;
    }

    public static double min(double[] values) {
        double aux = Double.MAX_VALUE;
        for (double val : values) {
            if (val < aux) {
                aux = val;
            }
        }
        return aux;
    }

    public static double average(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double aux = 0;
        for (double val : values) {
            aux += val;
        }
        return aux / ((double) (values.length));
    }

    private double getEvaResult(int dimension) {
        double[] results;
        int i;

        results = new double[evaStat.length];
        for (i = 0; i < results.length; i++) {
            results[i] = evaStat[i][dimension];
        }
        if (multipleMode == MULTIPLE_MAX) {
            return max(results);
        } else if (multipleMode == MULTIPLE_AVG) {
            return average(results);
        } else if (multipleMode == MULTIPLE_MIN) {
            return min(results);
        } else {
            return -1;
        }
    }

    public synchronized boolean evaluate(String testSummary, String[] refSummaries) {
        boolean ret;
        ret = true;
        if (metric == ROUGE_N) {
            computeRougeN(testSummary, refSummaries);
        } else if (metric == ROUGE_S) {
            computeRougeS(testSummary, refSummaries);
        } else if (metric == ROUGE_L) {
            computeRougeL(testSummary, refSummaries);
        } else if (metric == ROUGE_SU) {
            computeRougeSU(testSummary, refSummaries);
        } else {
            ret = false;
        }
        return ret;
    }

    public void printResult() {
        int j, k;
        for (k = 0; k < 50; k++) {
            System.out.print("-");
        }
        System.out.println();
        for (j = 0; j < evaStat.length; j++) {
            System.out.println("ReferenceModel: " + (j + 1));
            System.out.println("Average_R: " + evaStat[j][0]);
            System.out.println("Average_P: " + evaStat[j][1]);
            System.out.println("Average_F: " + evaStat[j][2]);
            System.out.println();
        }
        for (k = 0; k < 50; k++) {
            System.out.print("-");
        }
        System.out.println();
    }

    private void computeRougeN(String testSummary, String[] refSummaries) {
        HashMap testHash, refHash;
        ArrayList testList, referenceList;
        int match, reference, test, j;
        testList = tokenize(testSummary);
        evaStat = new double[refSummaries.length][3];
        testHash = computeNgrams(testList, gram);
        test = testList.size() - gram + 1;
        for (j = 0; j < refSummaries.length; j++) {
            referenceList = tokenize(refSummaries[j]);
            refHash = computeNgrams(referenceList, gram);
            match = matchNgrams(testHash, refHash);
            reference = referenceList.size() - gram + 1;
            if (reference <= 0) {
                evaStat[j][0] = 0;
            } else {
                evaStat[j][0] = (double) match / reference;
            }
            if (test <= 0) {
                evaStat[j][1] = 0;
            } else {
                evaStat[j][1] = match / (double) test;
            }
            evaStat[j][2] = computeFScore(evaStat[j][1], evaStat[j][0]);
        }
    }

    private void computeRougeS(String testSummary, String[] refSummaries) {
        HashSet hashGrams;
        ArrayList testList, referenceList;
        int match, reference, test, j;
        testList = tokenize(testSummary);
        test = countSkipBigram(testList.size(), maxSkip);
        evaStat = new double[refSummaries.length][3];
        for (j = 0; j < refSummaries.length; j++) {
            referenceList = tokenize(refSummaries[j]);
            hashGrams = computeSkipBigram(referenceList, maxSkip);
            match = matchSkipBigram(testList, maxSkip, hashGrams);
            reference = countSkipBigram(testList.size(), maxSkip);
            if (reference <= 0) {
                evaStat[j][0] = 0;
            } else {
                evaStat[j][0] = (double) match / reference;
            }
            if (test <= 0) {
                evaStat[j][1] = 0;
            } else {
                evaStat[j][1] = match / (double) test;
            }
            evaStat[j][2] = computeFScore(evaStat[j][1], evaStat[j][0]);
        }
    }

    private void computeRougeSU(String testSummary, String[] refSummaries) {
        HashSet hashGrams;
        ArrayList testList, referenceList;
        int match, reference, test, j;
        testList = tokenize(testSummary);
        test = countSkipBigram(testList.size(), maxSkip) + testList.size();
        evaStat = new double[refSummaries.length][3];
        for (j = 0; j < refSummaries.length; j++) {
            referenceList = tokenize(refSummaries[j]);
            hashGrams = computeSkipBigram(referenceList, maxSkip);
            match = matchSkipBigram(testList, maxSkip, hashGrams);
            reference = countSkipBigram(testList.size(), maxSkip)
                    + referenceList.size();
            if (reference <= 0) {
                evaStat[j][0] = 0;
            } else {
                evaStat[j][0] = (double) match / reference;
            }
            if (test <= 0) {
                evaStat[j][1] = 0;
            } else {
                evaStat[j][1] = match / (double) test;
            }
            evaStat[j][2] = computeFScore(evaStat[j][1], evaStat[j][0]);
        }
    }

    private void computeRougeL(String testSummary, String[] refSummaries) {
        String curSent;
        int match, reference, test, j;
        test = tokenize(testSummary).size();
        evaStat = new double[refSummaries.length][3];
        for (j = 0; j < refSummaries.length; j++) {
            match = 0;
            char[] cs = refSummaries[j].toCharArray();
            Iterator<Chunk> sentenceChunking = mSentenceChunker.chunk(cs, 0, cs.length).chunkSet().iterator();
            while (sentenceChunking.hasNext()) {
                Chunk aux = sentenceChunking.next();
                curSent = refSummaries[j].substring(aux.start(), aux.end());
                match += matchLCS(curSent, testSummary);
            }
            reference = tokenize(refSummaries[j]).size();
            if (reference <= 0) {
                evaStat[j][0] = 0;
            } else {
                evaStat[j][0] = (double) match / reference;
            }
            if (test <= 0) {
                evaStat[j][1] = 0;
            } else {
                evaStat[j][1] = match / (double) test;
            }
            evaStat[j][2] = computeFScore(evaStat[j][1], evaStat[j][0]);
        }
    }

    private HashMap computeNgrams(ArrayList wordList, int nGram) {
        HashMap hashGrams;
        Integer counter;
        String gramStr;
        int start, end;
        start = 0;
        end = nGram;
        hashGrams = new HashMap();
        while (end <= wordList.size()) {
            gramStr = getNgram(wordList, start, end);
            counter = (Integer) hashGrams.get(gramStr);
            if (counter != null) {
                counter += 1;
                hashGrams.put(gramStr, counter);
            } else {
                hashGrams.put(gramStr, new Integer(1));
            }
            start = start + 1;
            end = end + 1;
        }
        return hashGrams;
    }

    private int matchNgrams(HashMap testHash, HashMap refMap) {
        Iterator iterator;
        String gramStr;
        Integer testCounter, refCounter;
        int count;
        count = 0;
        iterator = testHash.keySet().iterator();
        while (iterator.hasNext()) {
            gramStr = (String) iterator.next();
            testCounter = (Integer) testHash.get(gramStr);
            refCounter = (Integer) refMap.get(gramStr);
            if (refCounter != null) {
                count += Math.min(testCounter, refCounter);
            }
        }
        return count;
    }

    private String getNgram(ArrayList wordList, int start, int end) {
        String gramStr;
        int i;
        gramStr = null;
        for (i = start; i < end; i++) {
            if (i == 0) {
                gramStr = ((String) wordList.get(i));
            } else {
                gramStr = gramStr + "\t" + ((String) wordList.get(i));
            }
        }
        return gramStr;
    }

    private HashSet computeSkipBigram(ArrayList list, int maxSkip) {
        HashSet hash;
        int i, start, end, first, second;
        hash = new HashSet();
        start = 0;
        end = Math.min(start + maxSkip + 1, list.size() - 1);
        while (start < end) {
            first = getIndex(list, start);
            for (i = start + 1; i <= end; i++) {
                second = getIndex(list, i);
                hash.add(new SimplePair(hash.size(), first, second));
            }
            start = start + 1;
            end = Math.min(start + maxSkip + 1, list.size() - 1);
        }
        return hash;
    }

    private int matchSkipBigram(ArrayList list, int maxSkip, HashSet reference) {
        int i, start, end, first, second, count;
        start = 0;
        count = 0;
        end = Math.min(start + maxSkip + 1, list.size() - 1);
        while (start < end) {
            first = getIndex(list, start);
            for (i = start + 1; i <= end; i++) {
                second = getIndex(list, i);
                if (reference.contains(new SimplePair(-1, first, second))) {
                    count++;
                }
            }
            start = start + 1;
            end = Math.min(start + maxSkip + 1, list.size() - 1);
        }
        return count;
    }

    private int countSkipBigram(int textLength, int maxSkip) {
        int start, end, count;
        start = 0;
        count = 0;
        end = Math.min(start + maxSkip + 1, textLength - 1);
        while (start < end) {
            count += end - start;
            start = start + 1;
            end = Math.min(start + maxSkip + 1, textLength - 1);
        }
        return count;
    }

    private int matchLCS(String refSent, String testDoc) {
        ArrayList list;
        ArrayList refList, testList, lcsList;
        String curSent;
        int i;
        list = new ArrayList();
        refList = tokenize(refSent);
        char[] cs = testDoc.toCharArray();
        Iterator<Chunk> sentenceChunking = mSentenceChunker.chunk(cs, 0, cs.length).chunkSet().iterator();
        while (sentenceChunking.hasNext()) {
            Chunk aux = sentenceChunking.next();
            curSent = testDoc.substring(aux.start(), aux.end());
            testList = tokenize(curSent);
            lcsList = computeLCS(refList, testList);
            for (i = 0; i < lcsList.size(); i++) {
                list.add(lcsList.get(i));
            }
        }
        return list.size();
    }

    private ArrayList computeLCS(ArrayList first, ArrayList second) {
        return null;
    }

    private int getIndex(ArrayList list, int pos) {
        // TODO:
        return pos;
    }

    private ArrayList tokenize(String sent) {
        ArrayList list = new ArrayList();
        String curToken;
        int i;
        char[] cs = sent.toCharArray();
        Tokenizer tokenizer = tokenExtractor.tokenizer(cs, 0, cs.length);
        String[] tokens = tokenizer.tokenize();
        if (!caseSensitive) {
            for (i = 0; i < tokens.length; i++) {
                tokens[i] = tokens[i].toLowerCase();
            }
        }
        for (String token : tokens) {
            list.add(token);
        }
        return list;
    }

    private double computeFScore(double precision, double recall) {
        if (precision == 0 || recall == 0) {
            return 0;
        } else if (beta == Double.MAX_VALUE) {
            return recall;
        } else {
            return (1 + beta * beta) * precision * recall
                    / (recall + beta * beta * precision);
        }
    }

    public static void main(String args[]) {
        Rouge rouge = new Rouge();
        String[] summaries = new String[]{"I like apples", "I love apples", "I enjoy apples"};
        String reference = "I dislike apples";
        rouge.evaluate(reference, summaries);
        for (int i = 0; i < rouge.evaStat.length; i++) {
            for (int j = 0; j < rouge.evaStat[i].length; j++) {
                System.out.print(rouge.evaStat[i][j]+"\t");
            }
            System.out.println();
        }
        System.out.println(rouge.getEvaResult(0));
        System.out.println(rouge.getEvaResult(1));
    }
}

class SimplePair {

    int key;
    int val1;
    int val2;

    public SimplePair(int key, int val1, int val2) {
        this.key = key;
        this.val1 = val1;
        this.val2 = val2;
    }

    public int compareTo(Object aux) {
        if (aux instanceof SimplePair) {
            return new Integer(key).compareTo(new Integer(((SimplePair) (aux)).key));
        }
        return -1;
    }
}