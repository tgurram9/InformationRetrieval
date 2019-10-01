package edu.stanford.nlp.process;

import java.util.Scanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.*;
import java.util.TreeMap;
import java.util.*;
import java.util.Map.Entry;
import org.json.*;
import java.util.Collections;
import java.util.Comparator;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;

class Stemmer {
	private char[] b;
	private int i, /* offset into b */
			i_end, /* offset to end of stemmed word */
			j, k;
	private static final int INC = 50;

	/* unit of size whereby b is increased */
	public Stemmer() {
		b = new char[INC];
		i = 0;
		i_end = 0;
	}

	/**
	 * Add a character to the word being stemmed. When you are finished adding
	 * characters, you can call stem(void) to stem the word.
	 */

	public void add(char ch) {
		if (i == b.length) {
			char[] new_b = new char[i + INC];
			for (int c = 0; c < i; c++)
				new_b[c] = b[c];
			b = new_b;
		}
		b[i++] = ch;
	}

	/**
	 * Adds wLen characters to the word being stemmed contained in a portion of
	 * a char[] array. This is like repeated calls of add(char ch), but faster.
	 */

	public void add(char[] w, int wLen) {
		if (i + wLen >= b.length) {
			char[] new_b = new char[i + wLen + INC];
			for (int c = 0; c < i; c++)
				new_b[c] = b[c];
			b = new_b;
		}
		for (int c = 0; c < wLen; c++)
			b[i++] = w[c];
	}

	/**
	 * After a word has been stemmed, it can be retrieved by toString(), or a
	 * reference to the internal buffer can be retrieved by getResultBuffer and
	 * getResultLength (which is generally more efficient.)
	 */
	public String toString() {
		return new String(b, 0, i_end);
	}

	/**
	 * Returns the length of the word resulting from the stemming process.
	 */
	public int getResultLength() {
		return i_end;
	}

	/**
	 * Returns a reference to a character buffer containing the results of the
	 * stemming process. You also need to consult getResultLength() to determine
	 * the length of the result.
	 */
	public char[] getResultBuffer() {
		return b;
	}

	/* cons(i) is true <=> b[i] is a consonant. */

	private final boolean cons(int i) {
		switch (b[i]) {
		case 'a':
		case 'e':
		case 'i':
		case 'o':
		case 'u':
			return false;
		case 'y':
			return (i == 0) ? true : !cons(i - 1);
		default:
			return true;
		}
	}

	/*
	 * m() measures the number of consonant sequences between 0 and j. if c is a
	 * consonant sequence and v a vowel sequence, and <..> indicates arbitrary
	 * presence,
	 * 
	 * <c><v> gives 0 <c>vc<v> gives 1 <c>vcvc<v> gives 2 <c>vcvcvc<v> gives 3
	 * ....
	 */

	private final int m() {
		int n = 0;
		int i = 0;
		while (true) {
			if (i > j)
				return n;
			if (!cons(i))
				break;
			i++;
		}
		i++;
		while (true) {
			while (true) {
				if (i > j)
					return n;
				if (cons(i))
					break;
				i++;
			}
			i++;
			n++;
			while (true) {
				if (i > j)
					return n;
				if (!cons(i))
					break;
				i++;
			}
			i++;
		}
	}

	/* vowelinstem() is true <=> 0,...j contains a vowel */

	private final boolean vowelinstem() {
		int i;
		for (i = 0; i <= j; i++)
			if (!cons(i))
				return true;
		return false;
	}

	/* doublec(j) is true <=> j,(j-1) contain a double consonant. */

	private final boolean doublec(int j) {
		if (j < 1)
			return false;
		if (b[j] != b[j - 1])
			return false;
		return cons(j);
	}

	/*
	 * cvc(i) is true <=> i-2,i-1,i has the form consonant - vowel - consonant
	 * and also if the second c is not w,x or y. this is used when trying to
	 * restore an e at the end of a short word. e.g.
	 * 
	 * cav(e), lov(e), hop(e), crim(e), but snow, box, tray.
	 * 
	 */

	private final boolean cvc(int i) {
		if (i < 2 || !cons(i) || cons(i - 1) || !cons(i - 2))
			return false;
		{
			int ch = b[i];
			if (ch == 'w' || ch == 'x' || ch == 'y')
				return false;
		}
		return true;
	}

	private final boolean ends(String s) {
		int l = s.length();
		int o = k - l + 1;
		if (o < 0)
			return false;
		for (int i = 0; i < l; i++)
			if (b[o + i] != s.charAt(i))
				return false;
		j = k - l;
		return true;
	}

	/*
	 * setto(s) sets (j+1),...k to the characters in the string s, readjusting
	 * k.
	 */

	private final void setto(String s) {
		int l = s.length();
		int o = j + 1;
		for (int i = 0; i < l; i++)
			b[o + i] = s.charAt(i);
		k = j + l;
	}

	/* r(s) is used further down. */

	private final void r(String s) {
		if (m() > 0)
			setto(s);
	}

	/*
	 * step1() gets rid of plurals and -ed or -ing. e.g.
	 * 
	 * caresses -> caress ponies -> poni ties -> ti caress -> caress cats -> cat
	 * 
	 * feed -> feed agreed -> agree disabled -> disable
	 * 
	 * matting -> mat mating -> mate meeting -> meet milling -> mill messing ->
	 * mess
	 * 
	 * meetings -> meet
	 * 
	 */

	private final void step1() {
		if (b[k] == 's') {
			if (ends("sses"))
				k -= 2;
			else if (ends("ies"))
				setto("i");
			else if (b[k - 1] != 's')
				k--;
		}
		if (ends("eed")) {
			if (m() > 0)
				k--;
		} else if ((ends("ed") || ends("ing")) && vowelinstem()) {
			k = j;
			if (ends("at"))
				setto("ate");
			else if (ends("bl"))
				setto("ble");
			else if (ends("iz"))
				setto("ize");
			else if (doublec(k)) {
				k--;
				{
					int ch = b[k];
					if (ch == 'l' || ch == 's' || ch == 'z')
						k++;
				}
			} else if (m() == 1 && cvc(k))
				setto("e");
		}
	}

	/* step2() turns terminal y to i when there is another vowel in the stem. */

	private final void step2() {
		if (ends("y") && vowelinstem())
			b[k] = 'i';
	}

	/*
	 * step3() maps double suffices to single ones. so -ization ( = -ize plus
	 * -ation) maps to -ize etc. note that the string before the suffix must
	 * give m() > 0.
	 */

	private final void step3() {
		if (k == 0)
			return;
		/* For Bug 1 */ switch (b[k - 1]) {
		case 'a':
			if (ends("ational")) {
				r("ate");
				break;
			}
			if (ends("tional")) {
				r("tion");
				break;
			}
			break;
		case 'c':
			if (ends("enci")) {
				r("ence");
				break;
			}
			if (ends("anci")) {
				r("ance");
				break;
			}
			break;
		case 'e':
			if (ends("izer")) {
				r("ize");
				break;
			}
			break;
		case 'l':
			if (ends("bli")) {
				r("ble");
				break;
			}
			if (ends("alli")) {
				r("al");
				break;
			}
			if (ends("entli")) {
				r("ent");
				break;
			}
			if (ends("eli")) {
				r("e");
				break;
			}
			if (ends("ousli")) {
				r("ous");
				break;
			}
			break;
		case 'o':
			if (ends("ization")) {
				r("ize");
				break;
			}
			if (ends("ation")) {
				r("ate");
				break;
			}
			if (ends("ator")) {
				r("ate");
				break;
			}
			break;
		case 's':
			if (ends("alism")) {
				r("al");
				break;
			}
			if (ends("iveness")) {
				r("ive");
				break;
			}
			if (ends("fulness")) {
				r("ful");
				break;
			}
			if (ends("ousness")) {
				r("ous");
				break;
			}
			break;
		case 't':
			if (ends("aliti")) {
				r("al");
				break;
			}
			if (ends("iviti")) {
				r("ive");
				break;
			}
			if (ends("biliti")) {
				r("ble");
				break;
			}
			break;
		case 'g':
			if (ends("logi")) {
				r("log");
				break;
			}
		}
	}

	/* step4() deals with -ic-, -full, -ness etc. similar strategy to step3. */

	private final void step4() {
		switch (b[k]) {
		case 'e':
			if (ends("icate")) {
				r("ic");
				break;
			}
			if (ends("ative")) {
				r("");
				break;
			}
			if (ends("alize")) {
				r("al");
				break;
			}
			break;
		case 'i':
			if (ends("iciti")) {
				r("ic");
				break;
			}
			break;
		case 'l':
			if (ends("ical")) {
				r("ic");
				break;
			}
			if (ends("ful")) {
				r("");
				break;
			}
			break;
		case 's':
			if (ends("ness")) {
				r("");
				break;
			}
			break;
		}
	}

	/* step5() takes off -ant, -ence etc., in context <c>vcvc<v>. */

	private final void step5() {
		if (k == 0)
			return;
		/* for Bug 1 */ switch (b[k - 1]) {
		case 'a':
			if (ends("al"))
				break;
			return;
		case 'c':
			if (ends("ance"))
				break;
			if (ends("ence"))
				break;
			return;
		case 'e':
			if (ends("er"))
				break;
			return;
		case 'i':
			if (ends("ic"))
				break;
			return;
		case 'l':
			if (ends("able"))
				break;
			if (ends("ible"))
				break;
			return;
		case 'n':
			if (ends("ant"))
				break;
			if (ends("ement"))
				break;
			if (ends("ment"))
				break;
			/* element etc. not stripped before the m */
			if (ends("ent"))
				break;
			return;
		case 'o':
			if (ends("ion") && j >= 0 && (b[j] == 's' || b[j] == 't'))
				break;
			/* j >= 0 fixes Bug 2 */
			if (ends("ou"))
				break;
			return;
		/* takes care of -ous */
		case 's':
			if (ends("ism"))
				break;
			return;
		case 't':
			if (ends("ate"))
				break;
			if (ends("iti"))
				break;
			return;
		case 'u':
			if (ends("ous"))
				break;
			return;
		case 'v':
			if (ends("ive"))
				break;
			return;
		case 'z':
			if (ends("ize"))
				break;
			return;
		default:
			return;
		}
		if (m() > 1)
			k = j;
	}

	/* step6() removes a final -e if m() > 1. */

	private final void step6() {
		j = k;
		if (b[k] == 'e') {
			int a = m();
			if (a > 1 || a == 1 && !cvc(k - 1))
				k--;
		}
		if (b[k] == 'l' && doublec(k) && m() > 1)
			k--;
	}

	/**
	 * Stem the word placed into the Stemmer buffer through calls to add().
	 * Returns true if the stemming process resulted in a word different from
	 * the input. You can retrieve the result with
	 * getResultLength()/getResultBuffer() or toString().
	 */
	public void stem() {
		k = i - 1;
		if (k > 1) {
			step1();
			step2();
			step3();
			step4();
			step5();
			step6();
		}
		i_end = k + 1;
		i = 0;
	}
}

public class InformationRetrievalf {

	/* metaphone code implementation */
	// ABCDEFGHIJKLMNOPQRSTUVWXYZ
	private static final char[] DEFAULT_MAPPING = "vBKTvFKHvJKLMNvPKRSTvFW*YS".toCharArray();

	private static char map(char c) {
		return DEFAULT_MAPPING[c - 'A'];
	}

	private static int CODE_LENGTH = 6;

	public static String encode(final String string) {
		String word = string.toUpperCase();
		word = word.replaceAll("[^A-Z]", "");
		if (word.length() == 0) {
			return "";
		} else if (word.length() == 1) {
			return word;
		}
		word = word.replaceFirst("^[KGP]N", "N");
		word = word.replaceFirst("^WR", "R");
		word = word.replaceFirst("^AE", "E");
		word = word.replaceFirst("^PF", "F");
		word = word.replaceFirst("^WH", "W");
		word = word.replaceFirst("^X", "S");

		// Transform input string to all caps
		final char[] input = word.toCharArray();

		int code_index = 0;
		final char[] code = new char[CODE_LENGTH];

		// Save previous character of word
		char prev_c = '?';

		for (int i = 0; i < input.length && code_index < CODE_LENGTH; i++) {
			final char c = input[i];
			/*
			 * if (c!='C' && c == prev_c) { 43 // prev_c = c is unncessary 44
			 * continue; 45 } 46
			 */
			if (c == prev_c) {
				// Especial rule for double letters
				if (c == 'C') {
					// We have "cc". The first "c" has already been mapped
					// to "K".
					if (i < input.length - 1 && "EIY".indexOf(input[i + 1]) >= 0) {
						// Do nothing and let it do to cc[eiy] -> KS
					} else {
						// This "cc" is just one sound
						continue;
					}
				} else {
					// It is not "cc", so ignore the second letter
					continue;
				}
			}
			switch (c) {

			case 'A':
			case 'E':
			case 'I':
			case 'O':
			case 'U':
				// Keep a vowel only if it is the first letter
				if (i == 0)
					code[code_index++] = c;
				break;

			case 'F':
			case 'J':
			case 'L':
			case 'M':
			case 'N':
			case 'R':
				code[code_index++] = c;
				break;
			case 'Q':
			case 'V':
			case 'Z':
				code[code_index++] = map(c);
				break;

			// B -> B only if NOT MB$
			case 'B':
				if (!(i == input.length - 1 && code_index > 0 && code[code_index - 1] == 'M'))
					code[code_index++] = c;
				break;

			case 'C':
				if (i < input.length - 2 && input[i + 1] == 'I' && input[i + 2] == 'A')
					code[code_index++] = 'X';
				else if (i < input.length - 1 && input[i + 1] == 'H' && i > 0 && input[i - 1] != 'S')
					code[code_index++] = 'X';
				else if (i < input.length - 1 && "EIY".indexOf(input[i + 1]) >= 0)
					code[code_index++] = 'S';
				else
					code[code_index++] = 'K';
				break;

			case 'D':
				if (i < input.length - 2 && input[i + 1] == 'G' && "EIY".indexOf(input[i + 2]) >= 0)
					code[code_index++] = 'J';
				else
					code[code_index++] = 'T';
				break;

			case 'G':
				if (i < input.length - 1 && input[i + 1] == 'N')
					; // GN -> N [GNED -> NED]
				else if (i > 0 && input[i - 1] == 'D' && i < input.length - 1 && "EIY".indexOf(input[i + 1]) >= 0)
					; // DG[IEY] -> D[IEY]
				else if (i < input.length - 1 && input[i + 1] == 'H'
						&& (i + 2 == input.length || "AEIOU".indexOf(input[i + 2]) < 0))
					;
				else if (i < input.length - 1 && "EIY".indexOf(input[i + 1]) >= 0)
					code[code_index++] = 'J';
				else
					code[code_index++] = map(c);
				break;

			case 'H':
				if (i > 0 && "AEIOUCGPST".indexOf(input[i - 1]) >= 0)
					; // vH -> v
				else if (i < input.length - 1 && "AEIOU".indexOf(input[i + 1]) < 0)
					; // Hc -> c
				else
					code[code_index++] = c;
				break;

			case 'K':
				if (i > 0 && input[i - 1] == 'C')
					; // CK -> K
				else
					code[code_index++] = map(c);
				break;

			case 'P':
				if (i < input.length - 1 && input[i + 1] == 'H')
					code[code_index++] = 'F';
				else
					code[code_index++] = map(c);
				break;

			case 'S':
				if (i < input.length - 2 && input[i + 1] == 'I' && (input[i + 2] == 'A' || input[i + 2] == 'O'))
					code[code_index++] = 'X';
				else if (i < input.length - 1 && input[i + 1] == 'H')
					code[code_index++] = 'X';
				else
					code[code_index++] = 'S';
				break;

			case 'T':
				// -TI[AO]- -> -XI[AO]-
				// -TCH- -> -CH-
				// -TH- -> -0-
				// -T- -> -T-
				if (i < input.length - 2 && input[i + 1] == 'I' && (input[i + 2] == 'A' || input[i + 2] == 'O'))
					code[code_index++] = 'X';
				else if (i < input.length - 1 && input[i + 1] == 'H')
					code[code_index++] = '0';
				else if (i < input.length - 2 && input[i + 1] == 'C' && input[i + 2] == 'H')
					; // drop letter
				else
					code[code_index++] = 'T';
				break;

			case 'W':
			case 'Y':
				// -Wv- -> -Wv-; -Wc- -> -c-
				// -Yv- -> -Yv-; -Yc- -> -c-
				if (i < input.length - 1 && "AEIOU".indexOf(input[i + 1]) >= 0)
					code[code_index++] = map(c);
				break;

			case 'X':
				// -X- -> -KS-
				code[code_index++] = 'K';
				if (code_index < code.length)
					code[code_index++] = 'S';
				break;

			default:
				assert(false);
			}
			prev_c = c;
		}
		return new String(code, 0, code_index);
	}

	
	private static HashMap<String, Double> sortByComparator(HashMap<String, Double> unsortMap, final boolean order) {

		List<Entry<String, Double>> list = new LinkedList<Entry<String, Double>>(unsortMap.entrySet());

		// Sorting the list based on values
		Collections.sort(list, new Comparator<Entry<String, Double>>() {
			public int compare(Entry<String, Double> o1, Entry<String, Double> o2) {
				if (order) {
					return o1.getValue().compareTo(o2.getValue());
				} else {
					return o2.getValue().compareTo(o1.getValue());

				}
			}
		});

		// Maintaining insertion order with the help of LinkedList
		HashMap<String, Double> sortedMap = new LinkedHashMap<String, Double>();
		for (Entry<String, Double> entry : list) {
			sortedMap.put(entry.getKey(), entry.getValue());
		}

		return sortedMap;
	}

	public static void printMap(HashMap<String, Double> map) {
		for (Entry<String, Double> entry : map.entrySet()) {
			System.out.println("Key : " + entry.getKey() + " Value : " + entry.getValue());
		}
	}

	public static void main(String[] args) throws FileNotFoundException, IOException, JSONException {
		// Query Input
		System.out.println("GIVE YOUR QUERY");
		Scanner input = new Scanner(System.in);
		String query;
		query = input.nextLine();
		input.close();
		// TreeMap 'queryMap'for storing keys(query terms) and value(query term
		// frequency).
		TreeMap<String, Integer> queryMap = new TreeMap<String, Integer>();
		// Query Tokenization begins
		PTBTokenizer<CoreLabel> ptbtQuery = new PTBTokenizer<>(new StringReader(query), new CoreLabelTokenFactory(), "");
		while (ptbtQuery.hasNext()) {
			CoreLabel queryToken = ptbtQuery.next();
			// Query Stemming begins
			Stemmer s = new Stemmer();
			String querystring = queryToken.toString();
			querystring = querystring.toLowerCase();
			for (int c = 0; c < querystring.length(); c++)
				s.add(querystring.charAt(c));
			s.stem();
			String queryTerm;
			queryTerm = s.toString();
			if (queryTerm.matches("[a-zA-Z][a-z]+")) {

				// Query Metaphone begins
				queryTerm = encode(queryTerm);
			}
			Integer freq = queryMap.get(queryTerm);
			queryMap.put(queryTerm, (freq == null) ? 1 : freq + 1);
		}

		// Corpus-retrieving of documents from json file
		String fileName = "C:/temp/pizza_request_dataset/pizza_request_dataset.json";
		String json = null;
		BufferedReader br = new BufferedReader(new FileReader(fileName));
		try {
			StringBuilder sb = new StringBuilder();
			String line = br.readLine();

			while (line != null) {
				sb.append(line);
				line = br.readLine();
			}
			json = sb.toString();
		} finally {
			br.close();
		}
		JSONArray JSONarray = new JSONArray(json);

		// 'FinalTermFrequencyMap' is the TreeMap that displays the final document with dictionary
		// terms as tokens and integer value as document frequency
		TreeMap<String, Integer> FinalTermFrequencyMap = new TreeMap<String, Integer>();

		// Making an array list of all the individual Treemaps that represent
		// individual documents (in terms of tokens and term frequency).
		ArrayList<TreeMap<String, Integer>> list = new ArrayList<TreeMap<String, Integer>>();
		for (int i = 0; i < JSONarray.length(); i++) {
			JSONObject object = JSONarray.getJSONObject(i);
			String request_data = null;
			request_data = object.getString("request_text");

			//Document Tokenization begins

			TreeMap<String, Integer> IndividualTermFrequency = new TreeMap<String, Integer>();

			PTBTokenizer<CoreLabel> ptbtDoc = new PTBTokenizer<>(new StringReader(request_data),
					new CoreLabelTokenFactory(), "");
			while (ptbtDoc.hasNext()) {
				CoreLabel docToken = ptbtDoc.next();
				//Document Stemming begins
				Stemmer s = new Stemmer();
				String docString = docToken.toString();
				docString = docString.toLowerCase();

				for (int c = 0; c < docString.length(); c++)
					s.add(docString.charAt(c));
				s.stem();
				String docTerm;
				docTerm = s.toString();
				if (docTerm.matches("[a-zA-Z][a-z]+")) {
				//Document Metaphone begins
					docTerm = encode(docTerm);
					}
				Integer freq = IndividualTermFrequency.get(docTerm);
				IndividualTermFrequency.put(docTerm, (freq == null) ? 1 : freq + 1);
			}
			for (Entry<String, Integer> entry : IndividualTermFrequency.entrySet()) {
				String key = entry.getKey();
				Integer freq = FinalTermFrequencyMap.get(key);
				FinalTermFrequencyMap.put(key, (freq == null) ? 1 : freq + 1);
			}

			list.add(IndividualTermFrequency);
		}
		//Total Number of Documents-'totalDocuments'
		int totalDocuments = list.size();
		TreeMap<String, Double> rankedProduct = new TreeMap<String, Double>();

		for (Entry<String, Integer> entry : FinalTermFrequencyMap.entrySet()) {

			String key = entry.getKey();
			Integer documentFrequency = entry.getValue();
			Double rankedValue = (totalDocuments - documentFrequency + 0.5) / (documentFrequency + 0.5);
			rankedProduct.put(key, rankedValue);
		}

		
		// Making a HashMap that contains dictionary tokens and their final
		// product value which would be used to keep ranking of documents
		HashMap<String, Double> unsortMap = new HashMap<String, Double>();
		int i = 1;
		for (TreeMap<String, Integer> d : list) {
			Double product = 1.00;
			for (Entry<String, Integer> entry : queryMap.entrySet()) {

				String key = entry.getKey();
				if (d.containsKey(key)) {
					product = product * (rankedProduct.get(key));

				}
			}
			unsortMap.put("Doc " + i, product);
            i++;
		}
		// Making a new HashMap that would sort the HashMap that contained key
		// and unsorted product ranks in descending order
		HashMap<String, Double> sortedMapDesc = sortByComparator(unsortMap, false);
		
		for (Entry<String, Double> entry: sortedMapDesc.entrySet()) {

			String key = entry.getKey();
			Double d = entry.getValue();
			System.out.println(key + "   " + d);
			
		}
		
	}
}
