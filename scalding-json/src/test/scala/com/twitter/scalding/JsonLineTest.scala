/*
Copyright 2012 Twitter, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.twitter.scalding.json

import org.specs._
import com.twitter.scalding.{JsonLine => StandardJsonLine, _}

import cascading.tuple.Fields
import cascading.tap.SinkMode

object JsonLine {
  def apply(p: String, fields: Fields = Fields.ALL) = new JsonLine(p, fields)
}
class JsonLine(p: String, fields: Fields) extends StandardJsonLine(p, fields, SinkMode.REPLACE) {
  // We want to test the actual tranformation here.
  override val transformInTest = true
}

class JsonLineJob(args : Args) extends Job(args) {
  try {
    Tsv("input0", ('query, 'queryStats)).read.write(JsonLine("output0"))
  } catch {
    case e : Exception => e.printStackTrace()
  }
}

class JsonLineRestrictedFieldsJob(args : Args) extends Job(args) {
  try {
    Tsv("input0", ('query, 'queryStats)).read.write(JsonLine("output0", Tuple1('query)))
  } catch {
    case e : Exception => e.printStackTrace()
  }
}

class JsonLineInputJob(args : Args) extends Job(args) {
  try {
    JsonLine("input0", ('foo, 'bar)).read
      .project('foo, 'bar)
      .write(Tsv("output0"))

  } catch {
    case e : Exception => e.printStackTrace
  }
}

class JsonLineTest extends Specification {
  noDetailedDiffs()
  import Dsl._

  "A JsonLine sink" should {
    JobTest(new JsonLineJob(_))
      .source(Tsv("input0", ('query, 'queryStats)), List(("doctor's mask", List(42.1f, 17.1f))))
      .sink[String](JsonLine("output0")) { buf =>
        val json = buf.head
        "not stringify lists or numbers and not escape single quotes" in {
            json must be_==("""{"query":"doctor's mask","queryStats":[42.1,17.1]}""")
        }
      }
      .run
      .finish

    JobTest(new JsonLineRestrictedFieldsJob(_))
      .source(Tsv("input0", ('query, 'queryStats)), List(("doctor's mask", List(42.1f, 17.1f))))
      .sink[String](JsonLine("output0", Tuple1('query))) { buf =>
        val json = buf.head
        "only sink requested fields" in {
            json must be_==("""{"query":"doctor's mask"}""")
        }
      }
      .run
      .finish

    val json = """{"foo": 3, "bar": "baz"}\n"""

    JobTest(new JsonLineInputJob(_))
      .source(JsonLine("input0", ('foo, 'bar)), List((0, json)))
      .sink[(Int, String)](Tsv("output0")) {
        outBuf =>
          "read json line input" in {
            outBuf.toList must be_==(List((3, "baz")))
          }
      }
      .run
      .finish

    val json2 = """{"foo": 7 }\n"""

    JobTest(new JsonLineInputJob(_))
      .source(JsonLine("input0", ('foo, 'bar)), List((0, json), (1, json2)))
      .sink[(Int, String)](Tsv("output0")) {
        outBuf =>
          "handle missing fields" in {
            outBuf.toList must be_==(List((3, "baz"), (7, null)))
          }
      }
      .run
      .finish
  }
 }
