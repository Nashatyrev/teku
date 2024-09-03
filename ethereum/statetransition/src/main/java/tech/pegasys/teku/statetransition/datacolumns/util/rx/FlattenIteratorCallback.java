/*
 * Copyright Consensys Software Inc., 2024
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.statetransition.datacolumns.util.rx;

class FlattenIteratorCallback<TCol extends Iterable<T>, T>
    extends AbstractDelegatingIteratorCallback<T, TCol> {

  protected FlattenIteratorCallback(AsyncIteratorCallback<T> delegate) {
    super(delegate);
  }

  @Override
  public boolean onNext(TCol t) {
    for (T elem : t) {
      if (!delegate.onNext(elem)) {
        return false;
      }
      ;
    }
    return true;
  }
}
