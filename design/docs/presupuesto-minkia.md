# CAPÍTULO II: PRESUPUESTO

## Presupuesto del proyecto MinkIA

> **Enfoque:** Costos reales de herramientas, plataformas y equipamiento (desembolso del equipo).
> **Moneda:** Soles peruanos (S/.) · **Tipo de cambio referencial:** US$ 1 ≈ S/ 3.80 (2026).
> **Horizonte:** 1 ciclo académico (~4 meses de desarrollo).

El proyecto MinkIA se sostiene sobre un **stack mayoritariamente gratuito y de código abierto**, lo que reduce drásticamente la inversión necesaria. El desarrollo nativo Android (Android Studio + Kotlin), el modelo de visión por computadora (YOLOv8) y el backend en la nube (Firebase) operan dentro de sus capas gratuitas durante toda la etapa académica. Por ello, el desembolso se concentra en un único pago de publicación y, opcionalmente, en hardware de prueba.

---

### 2.1. Software y herramientas de desarrollo

| Ítem | Detalle | Licencia | Costo (S/.) |
|------|---------|----------|------------:|
| Android Studio | IDE oficial de desarrollo Android | Gratuita | 0.00 |
| Kotlin SDK | Lenguaje nativo (patrón MVVM) | Gratuita | 0.00 |
| JDK + Gradle | Compilación y dependencias | Gratuita | 0.00 |
| Visual Studio Code | Editor auxiliar / scripts | Gratuita | 0.00 |
| Git + GitHub | Control de versiones (repos privados) | Gratuita | 0.00 |
| Figma | Diseño UI/UX y prototipado | Plan Free | 0.00 |
| YOLOv8 (Ultralytics) | Modelo de detección de objetos | Open Source (AGPL) | 0.00 |
| Python + librerías (PyTorch, OpenCV) | Entrenamiento del modelo IA | Open Source | 0.00 |
| **Subtotal software** | | | **0.00** |

---

### 2.2. Servicios en la nube (backend / IA)

| Ítem | Detalle | Plan | Costo (S/.) |
|------|---------|------|------------:|
| Firebase Authentication | Registro e inicio de sesión | Spark (gratuito) | 0.00 |
| Cloud Firestore | Base de datos en tiempo real | Spark (gratuito) | 0.00 |
| Firebase Storage | Almacenamiento de evidencias fotográficas | Spark (gratuito) | 0.00 |
| Google Maps Platform | Geolocalización y mapas de calor | Capa gratuita (crédito mensual) | 0.00 |
| Google Colab | Entrenamiento del modelo (GPU) | Free | 0.00 |
| **Subtotal nube** | | | **0.00** |

> **Nota:** En un escalamiento a producción con alto tráfico, Firebase pasaría al plan **Blaze** (pago por uso), estimado en **S/ 0 – 45 / mes** según volumen. En la etapa académica permanece en **S/ 0**.

---

### 2.3. Publicación y registro

| Ítem | Detalle | Tipo | Costo (S/.) |
|------|---------|------|------------:|
| Google Play Console | Cuenta de desarrollador (US$ 25) | Pago único | 95.00 |
| Dominio web .pe (opcional) | Landing/soporte del proyecto | Anual | 75.00 |
| **Subtotal publicación** | | | **170.00** |

---

### 2.4. Hardware y equipamiento

| Ítem | Detalle | Costo (S/.) |
|------|---------|------------:|
| Laptops del equipo | Equipo propio de los 5 integrantes (aporte) | 0.00 |
| Smartphone Android de prueba | Gama media, Android 5.0+ (1 unidad)\* | 650.00 |
| Cables, almacenamiento y varios | Periféricos de pruebas | 50.00 |
| **Subtotal hardware** | | | **700.00** |

> \* Costo **opcional**: S/ 0 si se utiliza un dispositivo propio del equipo para las pruebas en campo.

---

### 2.5. Conectividad y trabajo de campo (4 meses)

| Ítem | Detalle | Costo (S/.) |
|------|---------|------------:|
| Internet domiciliario | Aporte del hogar (prorrateo) | 0.00 |
| Energía eléctrica | Aporte del hogar | 0.00 |
| Datos móviles para pruebas en campo | Recolección de imágenes / GPS | 40.00 |
| Google Colab Pro (opcional, 1 mes) | GPU acelerada para entrenamiento | 40.00 |
| **Subtotal conectividad** | | | **80.00** |

---

### 2.6. Resumen del presupuesto

| Categoría | Costo (S/.) |
|-----------|------------:|
| 2.1. Software y herramientas | 0.00 |
| 2.2. Servicios en la nube | 0.00 |
| 2.3. Publicación y registro | 170.00 |
| 2.4. Hardware y equipamiento | 700.00 |
| 2.5. Conectividad y campo | 80.00 |
| **TOTAL ESTIMADO** | **S/ 950.00** |

#### Escenarios de inversión

| Escenario | Qué incluye | Total (S/.) |
|-----------|-------------|------------:|
| **Mínimo** | Solo lo indispensable: Play Console + datos móviles (todo lo demás gratuito y con equipo propio) | **135.00** |
| **Estimado (recomendado)** | Incluye smartphone de prueba, dominio y 1 mes de Colab Pro | **950.00** |
| **Recurrente en producción** | Mensual tras el lanzamiento (Firebase Blaze + dominio prorrateado) | **0 – 45 / mes** |

---

> **Nota sobre el capital humano:** El trabajo de los 5 integrantes (análisis, desarrollo Android, entrenamiento del modelo IA, diseño UI/UX y pruebas) se considera **aporte académico no valorizado**, propio de un proyecto del curso de Aplicaciones Móviles. En un escenario comercial, las **horas-hombre del equipo de desarrollo serían el componente principal del costo** del proyecto, muy por encima de las herramientas; el stack tecnológico gratuito mantiene la inversión en infraestructura prácticamente nula.
