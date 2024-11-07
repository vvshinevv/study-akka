package part7Test

object SampleTest extends App {
  def sum(list: List[Int]): Int = list match {
    case Nil     => 0 // 빈 리스트의 경우 0 반환
    case x :: xs => x + sum(xs) // 첫 번째 요소와 나머지 리스트의 합
  }

  // 예제 호출
  val numbers = List(1, 2, 3, 4)
  println(s"The sum is: ${sum(numbers)}") // The sum is: 10
}
