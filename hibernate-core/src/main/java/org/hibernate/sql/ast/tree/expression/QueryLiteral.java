/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast.tree.expression;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;

import org.hibernate.metamodel.mapping.BasicValuedMapping;
import org.hibernate.metamodel.mapping.ConvertibleModelPart;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.model.convert.spi.BasicValueConverter;
import org.hibernate.query.sqm.sql.internal.DomainResultProducer;
import org.hibernate.sql.ast.SqlAstWalker;
import org.hibernate.sql.ast.SqlTreeCreationException;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.exec.spi.ExecutionContext;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.basic.BasicResult;

/**
 * Represents a literal in the SQL AST.  This form accepts a {@link BasicValuedMapping}
 * as its {@link org.hibernate.metamodel.mapping.MappingModelExpressible}.
 *
 * @author Steve Ebersole
 * @see JdbcLiteral
 */
public class QueryLiteral<T> implements Literal, DomainResultProducer<T> {
	private final T value;
	private final BasicValuedMapping type;

	public QueryLiteral(T value, BasicValuedMapping type) {
		if ( type instanceof ConvertibleModelPart ) {
			final ConvertibleModelPart convertibleModelPart = (ConvertibleModelPart) type;
			final BasicValueConverter valueConverter = convertibleModelPart.getValueConverter();

			if ( valueConverter != null ) {
				final Object literalValue = value;
				final Object sqlLiteralValue;

				if ( literalValue == null || valueConverter.getDomainJavaType().getJavaTypeClass().isInstance(
						literalValue ) ) {
					sqlLiteralValue = valueConverter.toRelationalValue( literalValue );
				}
				else if ( valueConverter.getRelationalJavaType().getJavaTypeClass().isInstance( literalValue ) ) {
					sqlLiteralValue = literalValue;
				}
				else {
					throw new SqlTreeCreationException(
							String.format(
									Locale.ROOT,
									"QueryLiteral type [`%s`] did not match domain Java-type [`%s`] nor JDBC Java-type [`%s`]",
									literalValue.getClass(),
									valueConverter.getDomainJavaType().getJavaTypeClass().getName(),
									valueConverter.getRelationalJavaType().getJavaTypeClass().getName()
							)
					);
				}
				this.value = (T) sqlLiteralValue;
				this.type = convertibleModelPart;
			}
			else {
				this.value = value;
				this.type = type;
			}
		}
		else {
			this.value = value;
			this.type = type;
		}
	}

	@Override
	public T getLiteralValue() {
		return value;
	}

	@Override
	public JdbcMapping getJdbcMapping() {
		return type.getJdbcMapping();
	}

	@Override
	public void accept(SqlAstWalker walker) {
		walker.visitQueryLiteral( this );
	}

	@Override
	public BasicValuedMapping getExpressionType() {
		return type;
	}

	@Override
	public DomainResult<T> createDomainResult(
			String resultVariable,
			DomainResultCreationState creationState) {
		final SqlExpressionResolver sqlExpressionResolver = creationState.getSqlAstCreationState()
				.getSqlExpressionResolver();
		final SqlSelection sqlSelection = sqlExpressionResolver.resolveSqlSelection(
				this,
				type.getMappedType().getMappedJavaType(),
				null,
				creationState.getSqlAstCreationState()
						.getCreationContext()
						.getSessionFactory()
						.getTypeConfiguration()
		);

		return new BasicResult(
				sqlSelection.getValuesArrayPosition(),
				resultVariable,
				type.getMappedType().getMappedJavaType()
		);
	}

	@Override
	public void bindParameterValue(
			PreparedStatement statement,
			int startPosition,
			JdbcParameterBindings jdbcParameterBindings,
			ExecutionContext executionContext) throws SQLException {
		Object literalValue = getLiteralValue();
		// Convert the literal value if needed to the JDBC type on demand to still serve the domain model type through getLiteralValue()
		if ( type instanceof ConvertibleModelPart ) {
			ConvertibleModelPart convertibleModelPart = (ConvertibleModelPart) type;
			if ( convertibleModelPart.getValueConverter() != null ) {
				//noinspection unchecked
				literalValue = convertibleModelPart.getValueConverter().toRelationalValue( literalValue );
			}
		}
		//noinspection unchecked
		type.getJdbcMapping().getJdbcValueBinder().bind(
				statement,
				literalValue,
				startPosition,
				executionContext.getSession()
		);
	}

	@Override
	public void applySqlSelections(DomainResultCreationState creationState) {
		creationState.getSqlAstCreationState().getSqlExpressionResolver().resolveSqlSelection(
				this,
				type.getJdbcMapping().getJavaTypeDescriptor(),
				null,
				creationState.getSqlAstCreationState().getCreationContext().getMappingMetamodel().getTypeConfiguration()
		);
	}
}
