package com.github.luben.zstd;

/**
 * An abstract zstd sequence producer plugin. Subclasses of this class can be
 * passed to `ZstdCompressCtx.registerSequenceProducer()`.
 */
public abstract class AbstractSequenceProducer {
  /**
   * Returns a function pointer of type `ZSTD_sequenceProducer_F` that will be
   * passed to zstd as the `sequenceProducer` function. The returned pointer
   * must be valid for the duration of this object's lifetime.
   */
  abstract public long getProducerFunction();

  /**
   * Returns a `void *` pointer to the `sequenceProducerState`. The returned
   * pointer must be valid for the duration of this object's lifetime.
   */
  abstract public long getStatePointer();
}

/* Jacob's pseudocode - 
refers to ZSTD ZSTD_registerSequenceProducer: https://github.com/facebook/zstd/blob/69036dffe50f385bd3b7b187e3fd230f4b2ef97e/lib/zstd.h#L2801-L2825

public ZstdCompressCtx registerSequenceProducer(AbstractSequenceProducer producer) {
	if (producer == null) {
		ZSTD_registerSequenceProducer(cctx_pointer, 0, 0);
	} else {
		ZSTD_registerSequenceProducer(cctx_pointer, producer.getState(), producer.getProducer());
	}
	this.producer = producer;
}
*/