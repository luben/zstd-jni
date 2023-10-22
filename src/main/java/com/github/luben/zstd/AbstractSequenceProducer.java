package com.github.luben.zstd;

/**
 * Interface for an extenal sequence producer. The class implementing 
 * SequenceProducer must be provided by the user. To use, SequenceProducer should 
 * be passed to `ZstdCompressCtx.registerSequenceProducer()`.
 */
interface SequenceProducer {
  /**
   * Returns a function pointer of type `ZSTD_sequenceProducer_F` that will be
   * passed to zstd as the `sequenceProducer` function. The returned pointer
   * must be valid for the duration of this SeqeunceProducers's lifetime.
   */
  public long getProducerFunction();

  /**
   * Returns a `void *` pointer to the `sequenceProducerState`. The returned
   * pointer must be valid for the duration of this SeqeunceProducers's lifetime.
   */
  public long getStatePointer();
}