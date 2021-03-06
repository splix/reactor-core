/*
 * Copyright (c) 2011-2016 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.core

import reactor.core.publisher.EmitterProcessor
import reactor.core.scheduler.Schedulers
import reactor.core.util.ReactiveStateUtils
import spock.lang.Specification

import static reactor.core.publisher.Flux.*
import static reactor.core.subscriber.Subscribers.unbounded

/**
 * @author Stephane Maldini
 */
class ReactiveStateSpec extends Specification {

  def "Scan reactive streams"() {

	when: "Iterable publisher of 1000 to read queue"

	def pub1 = fromIterable(1..1000).map { d -> d }
	def pub2 = fromIterable(1..123).map { d -> d }

	def t = ReactiveStateUtils.scan(pub1)
	println t
	println t.nodes
	println t.edges


	then: "scan values correct"
	t.nodes
	t.edges

	when: "merged"

	def pub3 = merge(pub1, pub2)

	println "after merge"
	t = ReactiveStateUtils.scan(pub3)
	println t
	println t.nodes
	println t.edges

	then: "scan values correct"
	t.nodes
	t.edges

	when: "processors"

	def proc1 = EmitterProcessor.create()
	def proc2 = EmitterProcessor.create()
	def sub1 = unbounded()
	def sub2 = unbounded()
	def sub3 = unbounded()
	def _group = EmitterProcessor.create()
	proc1.log(" test").subscribe(sub1)
	_group.publishOn(Schedulers.single()).subscribe(sub2)
	proc1.log(" test").subscribe(sub3)
	proc1.log(" test").subscribe(_group)
	def zip = zip(pub3, proc2)

	t = ReactiveStateUtils.scan(zip)
	println t
	println t.nodes
	println t.edges

	zip.subscribe(proc1)

	println "after zip/subscribe"
	t = ReactiveStateUtils.scan(sub1)
	println t
	println t.nodes
	println t.edges

	then: "scan values correct"
	t.nodes

	cleanup:
	_group?.onComplete()
  }

}
