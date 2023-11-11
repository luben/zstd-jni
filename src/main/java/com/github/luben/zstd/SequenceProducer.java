package com.github.luben.zstd;

/**
 * Interface for an extenal sequence producer. To register a sequence producer,
 * pass an object implementing this interface to
 * {@link ZstdCompressCtx#registerSequenceProducer(SequenceProducer)}.
 */
public interface SequenceProducer {
  /**
   * Returns a pointer to the sequence producer function. This method is called
   * once in {@link ZstdCompressCtx#registerSequenceProducer(SequenceProducer)}.
   * @return A function pointer of type {@code ZSTD_sequenceProducer_F *}
   */
  public long getFunctionPointer();

  /**
   * Allocate the sequence producer state. The returned pointer will be passed
   * to the sequence producer function. This method is called once in
   * {@link ZstdCompressCtx#registerSequenceProducer(SequenceProducer)}.
   * @return A {@code void *} pointer to the sequence producer state
   */
  public long createState();

  /**
   * Free the sequence producer state. This method is called when closing the
   * {@link ZstdCompressCtx} or registering a different sequence producer.
   * @param statePointer the state pointer to be freed
   */
  public void freeState(long statePointer);
}