package com.example.data.repository

import com.example.data.database.JobDao
import com.example.data.database.JobEntity
import com.example.data.database.ServiceDao
import com.example.data.database.ServiceEntity
import kotlinx.coroutines.flow.Flow

class KslRepository(
    private val serviceDao: ServiceDao,
    private val jobDao: JobDao
) {
    val allServices: Flow<List<ServiceEntity>> = serviceDao.getAllServices()
    val allJobs: Flow<List<JobEntity>> = jobDao.getAllJobs()

    suspend fun insertService(service: ServiceEntity) = serviceDao.insertService(service)
    suspend fun updateService(service: ServiceEntity) = serviceDao.updateService(service)
    suspend fun deleteService(service: ServiceEntity) = serviceDao.deleteService(service)

    suspend fun insertJob(job: JobEntity): Long = jobDao.insertJob(job)
    suspend fun updateJob(job: JobEntity) = jobDao.updateJob(job)

    suspend fun syncWithCloud(onNewJobDetected: (JobEntity) -> Unit) {
        try {
            // 1. Get local jobs
            val localJobs = jobDao.getAllJobsOnce()
            val localUuids = localJobs.map { it.uuid }.toSet()

            // 2. Fetch cloud jobs from Firebase REST API
            val cloudJobsMap = com.example.data.api.RetrofitClient.cloudService.getCloudJobs()
            if (cloudJobsMap != null) {
                for ((_, cloudJob) in cloudJobsMap) {
                    // Check if the job is not already stored locally
                    if (!localUuids.contains(cloudJob.uuid)) {
                        val newJob = JobEntity(
                            uuid = cloudJob.uuid,
                            clientName = cloudJob.clientName,
                            description = cloudJob.description,
                            totalPrice = cloudJob.totalPrice,
                            timestamp = cloudJob.timestamp,
                            status = cloudJob.status,
                            isSync = true
                        )
                        jobDao.insertJob(newJob)
                        onNewJobDetected(newJob)
                    }
                }
            }

            // 3. Upload local unsynced jobs to Firebase RTDB
            val updatedLocalJobs = jobDao.getAllJobsOnce()
            for (job in updatedLocalJobs) {
                if (!job.isSync) {
                    val dto = com.example.data.api.CloudJobDto(
                        uuid = job.uuid,
                        clientName = job.clientName,
                        description = job.description,
                        totalPrice = job.totalPrice,
                        timestamp = job.timestamp,
                        status = job.status
                    )
                    com.example.data.api.RetrofitClient.cloudService.uploadJob(dto)
                    jobDao.updateJob(job.copy(isSync = true))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun seedInitialServicesIfEmpty() {
        if (serviceDao.getServiceCount() == 0) {
            val initialItems = listOf(
                // Services (isProduct = false)
                ServiceEntity(name = "Cambio de rodamientos delanteros", price = 2000.0, isProduct = false),
                ServiceEntity(name = "Cambio de pastillas de freno", price = 2000.0, isProduct = false),
                ServiceEntity(name = "Mantenimiento de pinza de freno ligero", price = 2000.0, isProduct = false),
                ServiceEntity(name = "Mantenimiento de pinza de freno medio", price = 4000.0, isProduct = false),
                ServiceEntity(name = "Mantenimiento de dirección de moto eléctrica", price = 4000.0, isProduct = false),
                ServiceEntity(name = "Cambio de rodamientos de dirección de moto eléctrica", price = 5000.0, isProduct = false),
                ServiceEntity(name = "Mantenimiento de dirección de Triciclo", price = 6000.0, isProduct = false),
                ServiceEntity(name = "Cambio de rodamientos de dirección de Triciclo", price = 8000.0, isProduct = false),
                ServiceEntity(name = "Mantenimiento de barras de moto eléctrica", price = 6000.0, isProduct = false),
                ServiceEntity(name = "Mantenimiento de barras de Triciclo", price = 8000.0, isProduct = false),
                ServiceEntity(name = "Mantenimiento mecánico ligero de bicimoto", price = 6000.0, isProduct = false),
                ServiceEntity(name = "Mantenimiento mecánico ligero de moto eléctrica", price = 8000.0, isProduct = false),
                ServiceEntity(name = "Mantenimiento de motor de moto eléctrica", price = 8000.0, isProduct = false),
                ServiceEntity(name = "Cambio de amortiguadores", price = 6000.0, isProduct = false),
                ServiceEntity(name = "Cambio de sensores de moto eléctrica", price = 6000.0, isProduct = false),
                ServiceEntity(name = "Cambio de sensores de motor de Triciclo", price = 8000.0, isProduct = false),
                ServiceEntity(name = "Mantenimiento de punta de ejes de Triciclo", price = 6000.0, isProduct = false),
                ServiceEntity(name = "Cambio de aceite de diferencial de Triciclo", price = 3000.0, isProduct = false),
                ServiceEntity(name = "Defectacion", price = 2000.0, isProduct = false),
                ServiceEntity(name = "Desmontaje de motor eléctrico de Triciclo para revisión", price = 2000.0, isProduct = false),
                ServiceEntity(name = "Montaje sencillo de motor eléctrico para Triciclo", price = 2000.0, isProduct = false),
                ServiceEntity(name = "Montaje y refaseo de motor eléctrico para Triciclo", price = 10000.0, isProduct = false),
                ServiceEntity(name = "Montaje de terminal eléctrico de fase de motor", price = 1000.0, isProduct = false),
                ServiceEntity(name = "Montaje de terminal de conexión", price = 1000.0, isProduct = false),
                ServiceEntity(name = "Soldadura de cables de potencia", price = 1000.0, isProduct = false),
                ServiceEntity(name = "Instalación de engaño de 72V a 60V", price = 10000.0, isProduct = false),
                ServiceEntity(name = "Instalación y configuración de GPS", price = 6000.0, isProduct = false),
                ServiceEntity(name = "Mantenimiento de motor de Triciclo", price = 8000.0, isProduct = false),

                // Products/Parts (isProduct = true)
                ServiceEntity(name = "Aceite de Transmisión (Pote)", price = 4500.0, isProduct = true),
                ServiceEntity(name = "Bombillo de Foco Delantero (LED)", price = 2500.0, isProduct = true),
                ServiceEntity(name = "Foco Auxiliar Pequeño", price = 3500.0, isProduct = true),
                ServiceEntity(name = "Fusible de Alta Potencia 40A", price = 500.0, isProduct = true),
                ServiceEntity(name = "Bujía Premium", price = 1800.0, isProduct = true),
                ServiceEntity(name = "Rodamiento Delantero de Moto", price = 3000.0, isProduct = true),
                ServiceEntity(name = "Pastillas de Freno (Par)", price = 3500.0, isProduct = true),
                ServiceEntity(name = "Bombillito de Repuesto 12V", price = 400.0, isProduct = true),
                ServiceEntity(name = "Cámara de Goma para Neumático", price = 5000.0, isProduct = true)
            )
            initialItems.forEach { serviceDao.insertService(it) }
        }
    }
}
