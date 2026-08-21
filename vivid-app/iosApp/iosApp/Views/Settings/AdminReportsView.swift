import FirebaseFirestore
import SwiftUI

private struct AdminReport: Identifiable {
    let id, targetType, targetId, reason, status: String
    let createdAt: Int64
}

struct AdminReportsView: View {
    @State private var reports: [AdminReport] = []
    @State private var error: String?
    var body: some View {
        List(reports) { report in
            VStack(alignment: .leading, spacing: 6) {
                HStack { Text(report.targetType.uppercased()).font(.caption.bold()); Spacer(); Text(report.status).font(.caption).foregroundStyle(.orange) }
                Text(report.reason)
                Text(report.targetId).font(.caption2).foregroundStyle(.secondary)
                HStack {
                    Button("Resolver") { update(report, status: "resolved") }.buttonStyle(.borderedProminent)
                    Button("Descartar") { update(report, status: "dismissed") }.buttonStyle(.bordered)
                }
            }.padding(.vertical, 6)
        }
        .navigationTitle("Reportes")
        .overlay { if let error { VStack { Image(systemName: "lock.shield"); Text(error).multilineTextAlignment(.center) }.padding() } }
        .task { await load() }
    }

    private func load() async {
        do {
            let snapshot = try await FirebaseAsync.value { completion in Firestore.firestore().collection("reports").whereField("status", isEqualTo: "open").order(by: "createdAt", descending: true).limit(to: 100).getDocuments(completion: completion) }
            reports = snapshot.documents.map { doc in
                let data = doc.data()
                return AdminReport(id: doc.documentID, targetType: data["targetType"] as? String ?? "contenido", targetId: data["targetId"] as? String ?? "", reason: data["reason"] as? String ?? "", status: data["status"] as? String ?? "open", createdAt: (data["createdAt"] as? NSNumber)?.int64Value ?? 0)
            }
        } catch { self.error = "Necesitas permisos de administrador." }
    }

    private func update(_ report: AdminReport, status: String) {
        Task {
            do { try await Firestore.firestore().collection("reports").document(report.id).updateDataAsync(["status": status, "reviewedAt": Int64(Date().timeIntervalSince1970 * 1_000)]); await load() }
            catch { self.error = error.localizedDescription }
        }
    }
}
