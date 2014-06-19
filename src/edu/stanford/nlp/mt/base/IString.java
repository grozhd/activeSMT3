package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

import java.io.Serializable;
import java.io.Writer;
import java.io.IOException;
import java.util.List;
import java.util.Collection;
import java.util.Iterator;

/**
 * Represents a String with a corresponding integer ID. Keeps a static index of
 * all the Strings, indexed by ID.
 *
 * @author danielcer
 *
 */
public class IString implements CharSequence, Serializable, HasIntegerIdentity,
    HasWord, Comparable<IString> {
  private static final long serialVersionUID = 2718L;

  // TODO(spenceg): Make this private and keep identityIndex() or
  // keep this public and remove identityIndex()
  public static final Index<String> index = new HashIndex<String>();

  public final int id;

  private enum Classing {
    BACKSLASH, IBM
  }
  private static final Classing CLASSING = Classing.IBM;

  /**
   * Constructor.
   * 
   * @param string
   */
  public IString(String string) {
    if (CLASSING == Classing.BACKSLASH) { // e.g., on december 4\\num
      int doubleBackSlashPos = string.indexOf("\\\\");
      if (doubleBackSlashPos != -1) {
        id = index.indexOf(string.substring(doubleBackSlashPos), true);
        return;
      }
    } else if (CLASSING == Classing.IBM) { // e.g., on december $num_(4)
      if (string.length() > 2 && string.startsWith("$")) {
        int delim = string.indexOf("_(");
        if (delim != -1 && string.endsWith(")")) {
          id = index.indexOf(string.substring(0, delim), true);
          return;
        }
      }
    }
    id = index.indexOf(string, true);
  }

  /**
   *
   */
  public IString(int id) {
    this.id = id;
  }

  @Override
  public char charAt(int charIndex) {
    return index.get(id).charAt(charIndex);
  }

  @Override
  public int length() {
    return index.get(id).length();
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    return index.get(id).subSequence(start, end);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if ( !(o instanceof IString)) {
      return false;
    } else {
      IString other = (IString) o;
      return this.id == other.id;
    }
  }

  @Override
  public int hashCode() {
    return id;
  }

  @Override
  public String toString() {
    return index.get(id);
  }

  @Override
  public int getId() {
    return id;
  }

  public static String getString(int id) {
    return index.get(id);
  }

  @Override
  public String word() {
    return toString();
  }

  @Override
  public void setWord(String word) {
    throw new UnsupportedOperationException();
  }

  private static WrapperIndex wrapperIndex; // = null;

  public static Index<IString> identityIndex() {
    if (wrapperIndex == null) {
      wrapperIndex = new WrapperIndex();
    }
    return wrapperIndex;
  }

  public static void load(String fileName) {
    for (String line : ObjectBank.getLineIterator(fileName)) {
      for (String word : line.split("\\s+")) {
        // System.err.println("adding: " + word);
        new IString(word);
      }
    }
  }

  private static class WrapperIndex implements Index<IString> {

    /**
     *
     */
    private static final long serialVersionUID = 2718L;

    @Override
    public boolean contains(Object o) {
      if (!(o instanceof IString))
        return false;
      return true; // all IStrings are in the index;
    }

    @Override
    public IString get(int i) {
      return new IString(index.get(i));
    }

    @Override
    public int indexOf(IString o) {
      return o.id;
    }

    @Override
    public int indexOf(IString o, boolean add) {
      return o.id;
    }

    @Override
    public int size() {
      return index.size();
    }

    @Override
    public List<IString> objectsList() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Collection<IString> objects(int[] ints) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isLocked() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void lock() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void unlock() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void saveToWriter(Writer out) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void saveToFilename(String s) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<IString> iterator() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] toArray(T[] a) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(IString iString) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends IString> c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public int compareTo(IString o) {
    return index.get(id).compareTo(index.get(o.id));
  }

}
