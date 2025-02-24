/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.graph;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.hibernate.graph.RootGraph;
import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.jdbc.SQLStatementInspector;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@TestForIssue(jiraKey = "HHH-15065")
@DomainModel(
	annotatedClasses = {
		HHH15065Test.Book.class,
		HHH15065Test.Person.class,
	}
)
@SessionFactory
class HHH15065Test {

	@Test
	void testDeterministicStatementWithEntityGraphHavingMultipleAttributes(SessionFactoryScope scope) throws Exception {
		scope.inSession( session -> {
			RootGraph<Book> entityGraph = session.createEntityGraph( Book.class );
			entityGraph.addAttributeNodes( "author", "coAuthor", "editor", "coEditor" );

			session.createQuery( "select book from Book book", Book.class )
				.setHint( "javax.persistence.fetchgraph", entityGraph )
				.getResultList();
		} );

		SQLStatementInspector statementInspector = (SQLStatementInspector) scope.getStatementInspector();
		List<String> sqlQueries = statementInspector.getSqlQueries();
		assertEquals( 1, sqlQueries.size() );
		assertEquals( "select b1_0.id,a1_0.id,c1_0.id,c2_0.id,e1_0.id" +
					 " from Book b1_0" +
					 " left join Person a1_0 on a1_0.id=b1_0.author_id" +
					 " left join Person c1_0 on c1_0.id=b1_0.coAuthor_id" +
					 " left join Person c2_0 on c2_0.id=b1_0.coEditor_id" +
					 " left join Person e1_0 on e1_0.id=b1_0.editor_id", sqlQueries.get(0) );
	}

	@Entity(name = "Book")
	public static class Book {
		@Id
		Long id;

		@ManyToOne
		Person author;

		@ManyToOne
		Person coAuthor;

		@ManyToOne
		Person editor;

		@ManyToOne
		Person coEditor;
	}

	@Entity(name = "Person")
	public class Person {
		@Id
		Long id;
	}

}
