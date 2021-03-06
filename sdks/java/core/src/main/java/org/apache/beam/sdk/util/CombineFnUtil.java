/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.sdk.util;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import org.apache.beam.sdk.coders.CannotProvideCoderException;
import org.apache.beam.sdk.coders.Coder;
import org.apache.beam.sdk.coders.CoderRegistry;
import org.apache.beam.sdk.transforms.Combine.CombineFn;
import org.apache.beam.sdk.transforms.Combine.KeyedCombineFn;
import org.apache.beam.sdk.transforms.CombineFnBase.GlobalCombineFn;
import org.apache.beam.sdk.transforms.CombineFnBase.PerKeyCombineFn;
import org.apache.beam.sdk.transforms.CombineWithContext.CombineFnWithContext;
import org.apache.beam.sdk.transforms.CombineWithContext.Context;
import org.apache.beam.sdk.transforms.CombineWithContext.KeyedCombineFnWithContext;
import org.apache.beam.sdk.transforms.display.DisplayData;
import org.apache.beam.sdk.util.state.StateContext;

/**
 * Static utility methods that create combine function instances.
 */
public class CombineFnUtil {
  /**
   * Returns the partial application of the {@link KeyedCombineFnWithContext} to a specific
   * context to produce a {@link KeyedCombineFn}.
   *
   * <p>The returned {@link KeyedCombineFn} cannot be serialized.
   */
  public static <K, InputT, AccumT, OutputT> KeyedCombineFn<K, InputT, AccumT, OutputT>
  bindContext(
      KeyedCombineFnWithContext<K, InputT, AccumT, OutputT> combineFn,
      StateContext<?> stateContext) {
    Context context = CombineContextFactory.createFromStateContext(stateContext);
    return new NonSerializableBoundedKeyedCombineFn<>(combineFn, context);
  }

  /**
   * Return a {@link CombineFnWithContext} from the given {@link GlobalCombineFn}.
   */
  public static <InputT, AccumT, OutputT>
      CombineFnWithContext<InputT, AccumT, OutputT> toFnWithContext(
          GlobalCombineFn<InputT, AccumT, OutputT> globalCombineFn) {
    if (globalCombineFn instanceof CombineFnWithContext) {
      @SuppressWarnings("unchecked")
      CombineFnWithContext<InputT, AccumT, OutputT> combineFnWithContext =
          (CombineFnWithContext<InputT, AccumT, OutputT>) globalCombineFn;
      return combineFnWithContext;
    } else {
      @SuppressWarnings("unchecked")
      final CombineFn<InputT, AccumT, OutputT> combineFn =
          (CombineFn<InputT, AccumT, OutputT>) globalCombineFn;
      return new CombineFnWithContext<InputT, AccumT, OutputT>() {
        @Override
        public AccumT createAccumulator(Context c) {
          return combineFn.createAccumulator();
        }
        @Override
        public AccumT addInput(AccumT accumulator, InputT input, Context c) {
          return combineFn.addInput(accumulator, input);
        }
        @Override
        public AccumT mergeAccumulators(Iterable<AccumT> accumulators, Context c) {
          return combineFn.mergeAccumulators(accumulators);
        }
        @Override
        public OutputT extractOutput(AccumT accumulator, Context c) {
          return combineFn.extractOutput(accumulator);
        }
        @Override
        public AccumT compact(AccumT accumulator, Context c) {
          return combineFn.compact(accumulator);
        }
        @Override
        public OutputT defaultValue() {
          return combineFn.defaultValue();
        }
        @Override
        public Coder<AccumT> getAccumulatorCoder(CoderRegistry registry, Coder<InputT> inputCoder)
            throws CannotProvideCoderException {
          return combineFn.getAccumulatorCoder(registry, inputCoder);
        }
        @Override
        public Coder<OutputT> getDefaultOutputCoder(
            CoderRegistry registry, Coder<InputT> inputCoder) throws CannotProvideCoderException {
          return combineFn.getDefaultOutputCoder(registry, inputCoder);
        }
        @Override
        public void populateDisplayData(DisplayData.Builder builder) {
          super.populateDisplayData(builder);
          combineFn.populateDisplayData(builder);
        }
      };
    }
  }

  /**
   * Return a {@link KeyedCombineFnWithContext} from the given {@link PerKeyCombineFn}.
   */
  public static <K, InputT, AccumT, OutputT> KeyedCombineFnWithContext<K, InputT, AccumT, OutputT>
  toFnWithContext(PerKeyCombineFn<K, InputT, AccumT, OutputT> perKeyCombineFn) {
    if (perKeyCombineFn instanceof KeyedCombineFnWithContext) {
      @SuppressWarnings("unchecked")
      KeyedCombineFnWithContext<K, InputT, AccumT, OutputT> keyedCombineFnWithContext =
          (KeyedCombineFnWithContext<K, InputT, AccumT, OutputT>) perKeyCombineFn;
      return keyedCombineFnWithContext;
    } else {
      @SuppressWarnings("unchecked")
      final KeyedCombineFn<K, InputT, AccumT, OutputT> keyedCombineFn =
          (KeyedCombineFn<K, InputT, AccumT, OutputT>) perKeyCombineFn;
      return new KeyedCombineFnWithContext<K, InputT, AccumT, OutputT>() {
        @Override
        public AccumT createAccumulator(K key, Context c) {
          return keyedCombineFn.createAccumulator(key);
        }
        @Override
        public AccumT addInput(K key, AccumT accumulator, InputT value, Context c) {
          return keyedCombineFn.addInput(key, accumulator, value);
        }
        @Override
        public AccumT mergeAccumulators(K key, Iterable<AccumT> accumulators, Context c) {
          return keyedCombineFn.mergeAccumulators(key, accumulators);
        }
        @Override
        public OutputT extractOutput(K key, AccumT accumulator, Context c) {
          return keyedCombineFn.extractOutput(key, accumulator);
        }
        @Override
        public AccumT compact(K key, AccumT accumulator, Context c) {
          return keyedCombineFn.compact(key, accumulator);
        }
        @Override
        public Coder<AccumT> getAccumulatorCoder(CoderRegistry registry, Coder<K> keyCoder,
            Coder<InputT> inputCoder) throws CannotProvideCoderException {
          return keyedCombineFn.getAccumulatorCoder(registry, keyCoder, inputCoder);
        }
        @Override
        public Coder<OutputT> getDefaultOutputCoder(CoderRegistry registry, Coder<K> keyCoder,
            Coder<InputT> inputCoder) throws CannotProvideCoderException {
          return keyedCombineFn.getDefaultOutputCoder(registry, keyCoder, inputCoder);
        }
        @Override
        public void populateDisplayData(DisplayData.Builder builder) {
          keyedCombineFn.populateDisplayData(builder);
        }
      };
    }
  }

  private static class NonSerializableBoundedKeyedCombineFn<K, InputT, AccumT, OutputT>
      extends KeyedCombineFn<K, InputT, AccumT, OutputT> {
    private final KeyedCombineFnWithContext<K, InputT, AccumT, OutputT> combineFn;
    private final Context context;

    private NonSerializableBoundedKeyedCombineFn(
        KeyedCombineFnWithContext<K, InputT, AccumT, OutputT> combineFn,
        Context context) {
      this.combineFn = combineFn;
      this.context = context;
    }
    @Override
    public AccumT createAccumulator(K key) {
      return combineFn.createAccumulator(key, context);
    }
    @Override
    public AccumT addInput(K key, AccumT accumulator, InputT value) {
      return combineFn.addInput(key, accumulator, value, context);
    }
    @Override
    public AccumT mergeAccumulators(K key, Iterable<AccumT> accumulators) {
      return combineFn.mergeAccumulators(key, accumulators, context);
    }
    @Override
    public OutputT extractOutput(K key, AccumT accumulator) {
      return combineFn.extractOutput(key, accumulator, context);
    }
    @Override
    public AccumT compact(K key, AccumT accumulator) {
      return combineFn.compact(key, accumulator, context);
    }
    @Override
    public Coder<AccumT> getAccumulatorCoder(CoderRegistry registry, Coder<K> keyCoder,
        Coder<InputT> inputCoder) throws CannotProvideCoderException {
      return combineFn.getAccumulatorCoder(registry, keyCoder, inputCoder);
    }
    @Override
    public Coder<OutputT> getDefaultOutputCoder(CoderRegistry registry, Coder<K> keyCoder,
        Coder<InputT> inputCoder) throws CannotProvideCoderException {
      return combineFn.getDefaultOutputCoder(registry, keyCoder, inputCoder);
    }
    @Override
    public void populateDisplayData(DisplayData.Builder builder) {
      combineFn.populateDisplayData(builder);
    }

    private void writeObject(@SuppressWarnings("unused") ObjectOutputStream out)
        throws IOException {
      throw new NotSerializableException(
          "Cannot serialize the CombineFn resulting from CombineFnUtil.bindContext.");
    }
  }
}
