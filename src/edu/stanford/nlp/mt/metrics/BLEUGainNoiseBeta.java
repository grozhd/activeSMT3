package edu.stanford.nlp.mt.metrics;

import java.util.List;

import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.stats.ProbabilityDistribution;
import edu.stanford.nlp.mt.stats.DistributionFactory;
import edu.stanford.nlp.stats.Counter;

/**
 * A version of BLEU+1 with noise added to the precision component.
 * 
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
public class BLEUGainNoiseBeta<TK,FV> implements SentenceLevelMetric<TK, FV> {

  private static final int DEFAULT_ORDER = 4;
  
  private static double[] BETA_PARAMS = {2.0, 7.0};
  private final ProbabilityDistribution beta = new DistributionFactory.Beta();
  
  @Override
  public double score(int sourceId, Sequence<TK> source,
      List<Sequence<TK>> references, Sequence<TK> translation) {
    int minLength = Integer.MAX_VALUE;
    for (Sequence<TK> sentence : references) {
      if (sentence.size() < minLength) {
        minLength = sentence.size();
      }
    }
    
    double[] noise = beta.draw(BETA_PARAMS, null);
    double score = computeLocalSmoothScore(translation, references, DEFAULT_ORDER, true, noise[0]);
    
    // Scale BLEU score by length per Chiang's recommendation
    return score * (double) minLength;
  }

  
  private double computeLocalSmoothScore(Sequence<TK> seq,
      List<Sequence<TK>> refs, int order, boolean doNakovExtension, double noiseValue) {

    Counter<Sequence<TK>> candidateCounts = Metrics.getNGramCounts(seq,
        order);
    Counter<Sequence<TK>> maxReferenceCount = Metrics.getMaxNGramCounts(refs, order);

    Metrics.clipCounts(candidateCounts, maxReferenceCount);
    int seqSz = seq.size();
    int[] localPossibleMatchCounts = new int[order];
    for (int i = 0; i < order; i++) {
      localPossibleMatchCounts[i] = possibleMatchCounts(i, seqSz);
    }

    double[] localCounts = localMatchCounts(candidateCounts,order);
    int localC = seq.size();
    int[] refLengths = new int[refs.size()];
    for (int i = 0; i < refLengths.length; i++) {
      refLengths[i] = refs.get(i).size();
    }
    int localR = bestMatchLength(refLengths, seq.size());
    if (doNakovExtension) ++localR;

    double localLogBP;
    if (localC < localR) {
      localLogBP = 1 - localR / (1.0 * localC);
    } else {
      localLogBP = 0.0;
    }

    double[] localPrecisions = new double[order];
    for (int i = 0; i < order; i++) {
      if (i == 0 && !doNakovExtension) {
        localPrecisions[i] = (1.0 * localCounts[i])
            / localPossibleMatchCounts[i];
      } else {
        localPrecisions[i] = (localCounts[i] + 1.0)
            / (localPossibleMatchCounts[i] + 1.0);
      }
    }
    double logNgramPrecisionScore = 0;
    for (int i = 0; i < order; i++) {
      logNgramPrecisionScore += (1.0 / order)
          * Math.log(localPrecisions[i]);
    }
    
    // Add noise
    double prec = Math.exp(logNgramPrecisionScore);
    prec += noiseValue;
    logNgramPrecisionScore = Math.log(Math.min(1.0, prec));

    final double localScore = Math.exp(localLogBP + logNgramPrecisionScore);
    return localScore;
  }
  

  private static int possibleMatchCounts(int order, int length) {
    int d = length - order;
    return d >= 0 ? d : 0;
  }

  private static <TK> double[] localMatchCounts(Counter<Sequence<TK>> clippedCounts, int order) {
    double[] counts = new double[order];
    for (Sequence<TK> ngram : clippedCounts.keySet()) {
      double cnt = clippedCounts.getCount(ngram);
      if (cnt > 0.0) {
        int len = ngram.size();
        counts[len - 1] += cnt;
      }
    }

    return counts;
  }

  private static int bestMatchLength(int[] refLengths, int candidateLength) {
    int best = refLengths[0];
    for (int i = 1; i < refLengths.length; i++) {
      if (Math.abs(candidateLength - best) > Math.abs(candidateLength
          - refLengths[i])) {
        best = refLengths[i];
      }
    }
    return best;
  }
  
  
  @Override
  public void update(int sourceId, List<Sequence<TK>> references,
      Sequence<TK> translation) {}

  @Override
  public boolean isThreadsafe() {
    return true;
  }
}
