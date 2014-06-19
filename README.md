activeSMT
=========

Our system provides the tools for fully automated translaiton model training and decoding new input.
The system is built as a pipeline, so it can perform all the necessary steps at once.
User has the option to select only necessary steps in the configuration file: 
  - pipeline.props (!!! it is the main configuration file !!!)
Most of the settings are self-explanatory.

0. Data

We provide the example data with the system. You can download full training datasets at www.statmt.org/europarl/
You can see the "data" folder with the following subfolders:
  - train_full: We store the full training corpus here (2 files: in French and in English). We separate the
  -             full training data from the currently used training data to allow user to do repetetive trainings
  -             with different corpus sizes.
  -             !!! Once you have system running, you should substitute these files with big corpus files !!!
  -             
  - train: Here the currently used training corpus is stored. Usually it is a part of the full training data.
  -        This corpus is used for the alignment.
  -
  - dev: The development data set is used for the Tuning. It is a small dataset that is used to optimize the weights
  -      for different transaltion probability features (such as phrase translation probability, reordering probability,
  -      language model probability, etc.). You don't need to change these files.
  -      
  - test: The test set to evaluate the performance of the trained model. You don't need to change these files.
  - 
  - mono: The monolingual dataset that is used to estimate the language model. We provide the example language model 4gm.arpa.
  -       If you are using an external ARPA language model, you do not need this set.
  -       !!! If you want to build a new language model, you should substitute this file for a bigger one !!!
  -       
  - al: The dataset reserved for the Active Learning. We use the bilingual dataset to simulate the human translation.
  -     !!! If you are doing AL, once you have it running, you should substitute these files !!!


1. The system workflow

// For the full descprition please read the pdf file.

The main steps of the pipleine are:

  0. Cutting the training set: cutting the part of "the train_full" corpus to "train". Useful for repetetive experiments
  1. Preprocessing: lowercasing, tokenizing, removing too long and too short sentences
  2. Alignment: we use BerkeleyAligner 2.1 for the alignment of the training corpus
  3. Building the Language Model (LM): we use BerkeleyLM 1.1. (you can suppress this option and use your own language model)
  4. Phrase extraction: building phrase tables for the "dev" and "test" datasets. Phrasal 3.3.1 is used.
  5. Tuning: finding the optimal weights. Phrasal 3.3.1 is used.
  6. Decoding: translating the French part of "test" dataset to English. Phrasal 3.3.1 is used.
  7. Evaluation - computing the BLEU score: finding the score for the system's translation. Phrasal 3.3.1 is used.
  
  8 (OPTIONAL) Active Learning: if turned on, the active learning option produces the new training data that is added
                                to the "train" corpus. In the workflow provided it is done before step 2. Alignment.

User can specify which steps of the pipeline to enable in the pipeline.props file. However, there are some dependencies
between the different steps. For example, Tuning and Decoding can't be done without phrase tables - 4.Phrase extraction.

2. The default setup

The provided default setup has the following properties:
  - all data is already preprocessed
  - the Language Model is built
  - Active Learning is turned off

Therefore, the pipeline reduces to:

  0. Cutting the training set (user can experiment with different training set sizes)
  2. Alignment
  4. Phrase extraction
  5. Tuning
  6. Decoding
  7. Evaluation

As the result you should obtain the test set translation (".trans" file) and it's BLEU score (".bleu" file)





