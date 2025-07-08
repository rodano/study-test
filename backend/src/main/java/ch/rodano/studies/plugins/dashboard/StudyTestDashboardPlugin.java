package ch.rodano.studies.plugins.dashboard;

import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import ch.rodano.core.model.user.User;
import ch.rodano.core.plugins.dashboard.DashboardData;
import ch.rodano.core.plugins.dashboard.DashboardPlugin;

@Component
public class StudyTestDashboardPlugin implements DashboardPlugin {

	final private static DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private static final String FPI = "First patient in";
	private static final String LPI = "Last patient in";
	private static final String PR = "Patients registered";
	private static final String PO = "Patients ongoing";
	private static final String PW = "Patients withdrawn";

	private static final String PATIENTS_REGISTERED = """
			select count(*) as count
			from scope s
			inner join scope_ancestor sa on s.pk = sa.scope_fk
			where
			sa.ancestor_fk = 1
			and sa.ancestor_deleted  = 0
			and s.deleted = 0
			and s.scope_model_id = 'PATIENT';
		""";

	private static final String FIRST_LAST_PATIENT = """
			select min(date) as first_date, max(date) as last_date
			from event v
			inner join scope s on s.pk = v.scope_fk
			inner join scope_ancestor sa on s.pk = sa.scope_fk
			where
			v.event_model_id  = 'BASELINE'
			and sa.ancestor_fk = 1;
		""";

	private static final String ONGOING_WITHDRAWN_PATIENTS = """
			select state_id, count(*) as count, sum(count(*)) over() as total
			from scope s
			inner join workflow_status ws on ws.scope_fk = s.pk
			inner join scope_ancestor sa on sa.scope_fk = s.pk
			where
			sa.ancestor_fk = 1
			and sa.ancestor_deleted = 0
			and s.deleted = 0
			and ws.workflow_id = 'PATIENT_STATUS'
			and (ws.state_id = 'WITHDRAWN' or ws.state_id = 'ONGOING')
			group by ws.state_id;
		""";

	@Override
	public List<DashboardData> getGeneralInformation(final DSLContext create, final User user) {
		final List<DashboardData> dashValues = new ArrayList<>();

		// Count the registered patients
		final var patientsRegisteredQuery = create.resultQuery(PATIENTS_REGISTERED);
		try(final var patientRegisteredResult = patientsRegisteredQuery.fetchResultSet()) {
			while(patientRegisteredResult.next()) {
				final var patientsRegistered = patientRegisteredResult.getInt("count");
				dashValues.add(new DashboardData(PR, String.valueOf(patientsRegistered)));
			}
		}
		catch(final SQLException e) {
			throw new RuntimeException(e);
		}

		// Get the first and last patients enrollment dates
		final var firstLastPatientQuery = create.resultQuery(FIRST_LAST_PATIENT);
		try(final var firstLastPatientDatesRes = firstLastPatientQuery.fetchResultSet()) {
			while(firstLastPatientDatesRes.next()) {
				if (firstLastPatientDatesRes.getDate("first_date") != null){
					final var firstDate = ZonedDateTime.of(firstLastPatientDatesRes.getDate("first_date").toLocalDate().atStartOfDay(ZoneId.of("UTC")).toLocalDateTime(), ZoneId.of("UTC"));
					final var lastDate = ZonedDateTime.of(firstLastPatientDatesRes.getDate("last_date").toLocalDate().atStartOfDay(ZoneId.of("UTC")).toLocalDateTime(), ZoneId.of("UTC"));
					dashValues.add(new DashboardData(FPI, firstDate.format(DATE_TIME_FORMATTER)));
					dashValues.add(new DashboardData(LPI, lastDate.format(DATE_TIME_FORMATTER)));
				}
				else {
					dashValues.add(new DashboardData(FPI, "NA"));
					dashValues.add(new DashboardData(LPI, "NA"));
				}

			}
		}
		catch(final SQLException e) {
			throw new RuntimeException(e);
		}

		final var ongoingWithdrawnPatientsQuery = create.resultQuery(ONGOING_WITHDRAWN_PATIENTS);
		try(final var ongoingWithdrawnPatientsRes = ongoingWithdrawnPatientsQuery.fetchResultSet()) {
			while(ongoingWithdrawnPatientsRes.next()) {
				final var status = ongoingWithdrawnPatientsRes.getString("state_id");
				final var count = ongoingWithdrawnPatientsRes.getInt("count");
				final var total = ongoingWithdrawnPatientsRes.getInt("total");

				final var percentage = count / ((float) total) * 100;

				if(status.equals("ONGOING")) {
					dashValues.add(new DashboardData(PO, String.format("%d (%.1f%%)", count, percentage)));
				}
				else if(status.equals("WITHDRAWN")) {
					dashValues.add(new DashboardData(PW, String.format("%d (%.1f%%)", count, percentage)));
				}
			}
		}
		catch(final SQLException e) {
			throw new RuntimeException(e);
		}

		return dashValues;
	}
}
