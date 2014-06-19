package custom;

public class NGram implements Comparable<NGram> {
	
	private String text;
	private int sentenceNum; 
	
	NGram(String inputText, int inputSentenceNum) {
		this.text = inputText;
		this.sentenceNum = inputSentenceNum;
	}

	public String getText() {
		return text;
	}

	public int getSentenceNum() {
		return this.sentenceNum;
	}
	
	public int compareTo(NGram nGram) {
		return text.compareTo(nGram.getText());
	}
	
}
