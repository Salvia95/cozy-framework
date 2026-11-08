package {{pkg}};

{{imports}}/**
 * {{Record}}의 행 (cozy add query). SELECT의 열 이름이 구성 요소 이름과 같아야 한다(created_at 같은 밑줄 이름은 createdAt에 들어간다).
 * 저장 형식이 보이는 형식과 다르면 타입을 바꾸고 Finder 구현의 옮기는 코드를 고친다.
 */
public record {{Record}}Row({{components}}) {
}
