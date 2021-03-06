package org.clulab.wm.eidos.groundings

import org.clulab.embeddings.word2vec.Word2Vec
import org.clulab.odin.Mention
import org.clulab.wm.eidos.utils.Sourcer

trait EidosWordToVec {
  type Similarities = Seq[(String, Double)]
  type ConceptEmbeddings = Map[String, Seq[Double]]

  def stringSimilarity(s1: String, s2: String): Double
  def calculateSimilarity(m1: Mention, m2: Mention): Double
  def calculateSimilarities(canonicalNameParts: Array[String], conceptEmbeddings: ConceptEmbeddings): Similarities
  def makeCompositeVector(t:Iterable[String]): Array[Double]
}

class FakeWordToVec extends EidosWordToVec {

  override def stringSimilarity(s1: String, s2: String): Double =
    throw new RuntimeException("Word2Vec wasn't loaded, please check configurations.")

  def calculateSimilarity(m1: Mention, m2: Mention): Double = 0

  def calculateSimilarities(canonicalNameParts: Array[String], conceptEmbeddings: ConceptEmbeddings): Similarities = Seq.empty
//  def calculateSimilarities(canonicalNameParts: Array[String], conceptEmbeddings: Map[String, Seq[Double]]): Seq[(String, Double)] = Seq(("hello", 5.0d))

  def makeCompositeVector(t:Iterable[String]): Array[Double] = Array.emptyDoubleArray
}

class RealWordToVec(var w2v: Word2Vec, topKNodeGroundings: Int) extends EidosWordToVec {

  protected def split(string: String): Array[String] = string.split(" +")

  def stringSimilarity(s1: String, s2: String): Double =
      w2v.avgSimilarity(split(s1), split(s2))

  def calculateSimilarity(m1: Mention, m2: Mention): Double = {
    // avgSimilarity does sanitizeWord itself, so it is unnecessary here.
    val sanitisedM1 =  split(m1.text)
    val sanitisedM2 =  split(m2.text)
    val similarity = w2v.avgSimilarity(sanitisedM1, sanitisedM2)
    
    similarity
  }
  
  def calculateSimilarities(canonicalNameParts: Array[String], conceptEmbeddings: ConceptEmbeddings): Similarities = {
    val nodeEmbedding = w2v.makeCompositeVector(canonicalNameParts.map(word => Word2Vec.sanitizeWord(word)))
    val similarities = conceptEmbeddings.toSeq.map(concept => (concept._1, Word2Vec.dotProduct(concept._2.toArray, nodeEmbedding)))
    
    similarities.sortBy(- _._2).slice(0, topKNodeGroundings)
  }

  def makeCompositeVector(t: Iterable[String]): Array[Double] = w2v.makeCompositeVector(t)
}

object EidosWordToVec {

  def apply(enabled: Boolean, wordToVecPath: String, topKNodeGroundings: Int): EidosWordToVec = {
    if (enabled) {
      val source = Sourcer.sourceFromResource(wordToVecPath)

      try {
        val w2v = new Word2Vec(source, None)

        new RealWordToVec(w2v, topKNodeGroundings)
      }
      finally {
        source.close()
      }
    }
    else
      new FakeWordToVec()
  }
}
