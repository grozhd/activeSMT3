package edu.stanford.nlp.mt.stats;

/**
 * Sampling for various random distributions.
 * 
 * @author Spence Green
 *
 */
public final class DistributionFactory {

  private DistributionFactory() {}
  
  // Creates a random number generator on the first call
  // As of Java7, this call is threadsafe.
  protected static double uniformDraw() { return Math.random(); }

  public static class Bernoulli implements ProbabilityDistribution {

    //params[0] contains the probability of a success
    //Returns 0.0 for tails
    //        1.0 for heads
    @Override
    public double[] draw(double[] params, double[] hyperParams) {
      if(params.length == 0) 
        throw new IllegalArgumentException();

      double value = (uniformDraw() < params[0]) ? 1.0 : 0.0;
      double[] ret = {value};
      return ret;
    }

    @Override
    public double probOf(double[] instance) {
      throw new UnsupportedOperationException("Bernoulli is continuous...cannot compute probability of a point.");
    }

    @Override
    public double mean(double[] params, double[] hyperParams) {
      if(params.length < 1)
        throw new IllegalArgumentException();

      return params[0];
    }
  }

  public static class Beta implements ProbabilityDistribution {

    //params[0] = alpha
    //params[1] = beta
    //Set gamma scale parameter to 1
    @Override
    public double[] draw(double[] params, double[] hyperParams) {
      double[] ret = new double[1];

      if(params.length == 2) {
        double gammaA = Gamma.drawSample(params[0]);
        double gammaB = Gamma.drawSample(params[1]);
        ret[0] = gammaA / (gammaA + gammaB);
      } else
        throw new IllegalArgumentException("Beta distribution has two parameters");

      return ret;
    }

    @Override
    public double probOf(double[] instance) {
      throw new UnsupportedOperationException("Beta is continuous...cannot compute probability of a point.");
    }

    @Override
    public double mean(double[] params, double[] hyperParams) {
      if (params.length == 2) {
        return params[0] / (params[0] + params[1]);
      }
      throw new IllegalArgumentException("Beta distributions has two parameters");
    }
  }

  public static class Gamma implements ProbabilityDistribution {

    //params[0] == shape parameter
    //params[1] == scale parameter
    @Override
    public double[] draw(double[] params, double[] hyperParams) {
      double[] ret = new double[1];

      if(params.length == 1)
        ret[0] = drawSample(params[0]);
      else if(params.length == 2)
        ret[0] = drawSample(params[0])*params[1];
      else
        throw new IllegalArgumentException("Incorrect set of parameters");

      return ret;
    }

    //Copied from Jenny's code, which was copied from Teh's
    private static double drawSample(double alpha) {
      if (alpha <= 0.0) {
        /* Not well defined, set to zero and skip. */
        throw new IllegalArgumentException("Gamma distribution not defined for alpha = " + alpha);

      } else if ( alpha == 1.0 ) {
        /* Exponential */
        return -Math.log(uniformDraw());

      } else if (alpha < 1.0) {
        /* Use Johnks generator */
        double cc = 1.0 / alpha;
        double dd = 1.0 / (1.0-alpha);
        while (true) {
          double xx = Math.pow(uniformDraw(), cc);
          double yy = xx + Math.pow(uniformDraw(), dd);
          if (yy <= 1.0) {
            return -Math.log(uniformDraw()) * xx / yy;
          }
        }

      } else {
        /* Use bests algorithm */
        double bb = alpha - 1.0;
        double cc = 3.0 * alpha - 0.75;
        while (true) {
          double uu = uniformDraw();
          double vv = uniformDraw();
          double ww = uu * (1.0 - uu);
          double yy = Math.sqrt(cc / ww) * (uu - 0.5);
          double xx = bb + yy;
          if (xx >= 0) {
            double zz = 64.0 * ww * ww * ww * vv * vv;
            if ( ( zz <= (1.0 - 2.0 * yy * yy / xx) ) ||
                ( Math.log(zz) <= 2.0 * (bb * Math.log(xx / bb) - yy) ) ) {
              return xx;
            }
          }
        }
      }        
    }

    @Override
    public double probOf(double[] instance) {
      throw new UnsupportedOperationException("Gamma is continuous...cannot compute probability");
    }

    @Override
    public double mean(double[] params, double[] hyperParams) {
      if(params.length != 2)
        throw new IllegalArgumentException();
      return params[0]*params[1];
    }
  }


  /**
   * @param args
   */
  public static void main(String[] args) {
    double[] betaParams = {1.0,2.0};
    ProbabilityDistribution dist = new DistributionFactory.Beta();

    for(int i = 0; i < 10; i++) {
      System.out.printf("Beta Draw: %.5f%n", dist.draw(betaParams, null)[0]);
    }
  }
}
