// 기본 앱을 복사하고 recipe.txt를 실행한다 (../../recipes.groovy)
new GroovyShell(this.class.classLoader, binding).evaluate(new File(recipesScript))
