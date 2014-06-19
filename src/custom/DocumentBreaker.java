package custom;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class DocumentBreaker {
	public static void writeFirstNSentences(File in, File out, long N, long offset) {
		try {
		
		BufferedReader fR = new BufferedReader(new FileReader(in));
		BufferedWriter fW = new BufferedWriter(new FileWriter(out));
		
		String line = null;
		long counter = 0;
		
		while((line = fR.readLine()) != null) {
			
			if (counter >= offset && counter < N + offset) {
				fW.write(line);
				fW.newLine();
			}
			
			if (counter > N + offset) break;
			
			++counter;
		}
		
		fR.close();
		fW.close();
		
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
