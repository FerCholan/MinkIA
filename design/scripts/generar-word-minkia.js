// ============================================================
//  Generador del Word de MinkIA : estilo informe UNS (TurismoSanta)
//  Times New Roman 12 / interlineado 1.5 / justificado / tablas APA
//  Contenido: Cap. II Presupuesto  +  Cap. IV Prototipado e Identidad de Marca
//  Uso:  NODE_PATH=<.../minkia-shot/node_modules> node generar-word-minkia.js
// ============================================================
const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, ImageRun,
  HeadingLevel, AlignmentType, Table, TableRow, TableCell, WidthType,
  BorderStyle, VerticalAlign, ShadingType, Footer, PageNumber, PageOrientation
} = require("docx");

// Estructura: design/scripts/ (este archivo) · design/png/... · design/docs/
const DESIGN = path.resolve(__dirname, "..");
const PNG  = path.join(DESIGN, "png");
const WIRE = path.join(PNG, "wireframes");
const MOCK = path.join(PNG, "mockups");
const MARCA = path.join(PNG, "marca");
const DIAG  = path.join(PNG, "diagramas");
const FIG_PRINCIPAL    = path.join(MARCA, "logo-principal.png");
const FIG_ISOTIPO      = path.join(MARCA, "logo-isotipo.png");
const FIG_VERSIONES    = path.join(MARCA, "logo-versiones.png");
const FIG_CONSTRUCCION = path.join(MARCA, "logo-construccion.png");
const FIG_NAV_CIU      = path.join(DIAG, "nav-ciudadano.png");
const FIG_NAV_ADM      = path.join(DIAG, "nav-admin.png");
const OUT  = path.join(DESIGN, "docs", "MinkIA_Presupuesto_y_Prototipado.docx");

// ─── Bordes estilo APA (solo líneas horizontales) ───────────
const noBorder = { style: BorderStyle.NONE, size: 0, color: "FFFFFF" };
const solidBorder = { style: BorderStyle.SINGLE, size: 4, color: "000000" };
const tableBorders = {
  top: solidBorder, bottom: solidBorder,
  left: noBorder, right: noBorder,
  insideHorizontal: noBorder, insideVertical: noBorder,
};
const noBorders = { top: noBorder, bottom: noBorder, left: noBorder, right: noBorder, insideHorizontal: noBorder, insideVertical: noBorder };

// ─── Párrafos ───────────────────────────────────────────────
const p = (text, opts = {}) => new Paragraph({
  spacing: { after: 200, line: 360 },
  alignment: AlignmentType.JUSTIFIED,
  indent: { firstLine: 0 },
  ...opts,
  children: [new TextRun({ text, font: "Times New Roman", size: 24, ...opts })],
});

const H = (level, text, extra = {}) => {
  const lv = { 1: HeadingLevel.HEADING_1, 2: HeadingLevel.HEADING_2, 3: HeadingLevel.HEADING_3, 4: HeadingLevel.HEADING_4 }[level];
  const spacing = level === 1 ? { before: 120, after: 180 } : level === 2 ? { before: 300, after: 180 } : level === 3 ? { before: 240, after: 140 } : { before: 200, after: 100 };
  const size = level === 1 ? 28 : level === 4 ? 22 : 24;
  return new Paragraph({ heading: lv, spacing, ...extra, children: [new TextRun({ text, font: "Times New Roman", size, bold: true, color: "000000" })] });
};

const pCuadroTitle = (text) => new Paragraph({
  spacing: { before: 120, after: 120, line: 360 }, alignment: AlignmentType.LEFT,
  children: [new TextRun({ text, font: "Times New Roman", size: 24, bold: true })],
});

const pFuente = (text = "Elaboración propia.") => new Paragraph({
  spacing: { before: 60, after: 240, line: 360 }, alignment: AlignmentType.LEFT,
  children: [
    new TextRun({ text: "FUENTE: ", font: "Times New Roman", size: 22 }),
    new TextRun({ text, font: "Times New Roman", size: 22 }),
  ],
});

// ─── Celdas de tabla ────────────────────────────────────────
const th = (text, w = null) => {
  const o = {
    children: [new Paragraph({ children: [new TextRun({ text, bold: true, font: "Times New Roman", size: 20 })], alignment: AlignmentType.CENTER, spacing: { before: 60, after: 60 } })],
    borders: { top: solidBorder, bottom: solidBorder, left: noBorder, right: noBorder },
    margins: { top: 45, bottom: 45, left: 90, right: 90 }, verticalAlign: VerticalAlign.CENTER,
  };
  if (w) o.width = { size: w, type: WidthType.PERCENTAGE };
  return new TableCell(o);
};
const td = (text, align = AlignmentType.LEFT, bold = false) => new TableCell({
  children: [new Paragraph({ children: [new TextRun({ text, font: "Times New Roman", size: 20, bold })], alignment: align, spacing: { before: 60, after: 60 } })],
  margins: { top: 45, bottom: 45, left: 90, right: 90 }, verticalAlign: VerticalAlign.CENTER,
});
const tdTop = (text, align = AlignmentType.LEFT, bold = true) => new TableCell({
  children: [new Paragraph({ children: [new TextRun({ text, font: "Times New Roman", size: 20, bold })], alignment: align, spacing: { before: 60, after: 60 } })],
  borders: { top: solidBorder, bottom: noBorder, left: noBorder, right: noBorder },
  margins: { top: 45, bottom: 45, left: 90, right: 90 }, verticalAlign: VerticalAlign.CENTER,
});
const tdSwatch = (hex) => new TableCell({
  shading: { fill: hex, type: ShadingType.CLEAR, color: "auto" },
  children: [new Paragraph({ children: [new TextRun({ text: " ", size: 20 })] })],
  margins: { top: 45, bottom: 45, left: 90, right: 90 }, verticalAlign: VerticalAlign.CENTER,
});
// Celda con varios ítems, cada uno en su propia línea (misma fila)
const tdLines = (items) => new TableCell({
  margins: { top: 45, bottom: 45, left: 90, right: 90 }, verticalAlign: VerticalAlign.CENTER,
  children: items.map((t, i) => new Paragraph({
    spacing: { before: i === 0 ? 0 : 8, after: 8, line: 240 }, alignment: AlignmentType.LEFT,
    children: [new TextRun({ text: "•  " + t, font: "Times New Roman", size: 20 })],
  })),
});

// ─── Imágenes ───────────────────────────────────────────────
function pngSize(file) {
  const b = fs.readFileSync(file);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20) };
}
function imgRun(file, widthInches) {
  const { w, h } = pngSize(file);
  const wIn = widthInches, hIn = widthInches * (h / w);
  return new ImageRun({ data: fs.readFileSync(file), type: "png", transformation: { width: Math.round(wIn * 96), height: Math.round(hIn * 96) } });
}

let FIG = 0;
function figura(file, caption, widthInches) {
  FIG++;
  if (!fs.existsSync(file)) return [p("[Imagen no encontrada: " + path.basename(file) + "]", { italics: true, alignment: AlignmentType.CENTER })];
  return [
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 160, after: 60 }, children: [imgRun(file, widthInches)] }),
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 0, after: 220 }, children: [new TextRun({ text: `Figura ${FIG}. ${caption}.`, font: "Times New Roman", size: 20, italics: true })] }),
  ];
}

// Par de pantallas: wireframe (izquierda) + mockup (derecha)
function par(role, id, title, slug, num, desc) {
  FIG++;
  const wf = path.join(WIRE, role, `${id}-${slug}.png`);
  const mk = path.join(MOCK, role, `${id}-${slug}.png`);
  const W = 2.2;
  const cell = (file) => new TableCell({
    width: { size: 50, type: WidthType.PERCENTAGE }, borders: noBorders,
    margins: { top: 40, bottom: 40, left: 40, right: 40 }, verticalAlign: VerticalAlign.CENTER,
    children: [new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 0 }, children: fs.existsSync(file) ? [imgRun(file, W)] : [new TextRun({ text: "[falta " + path.basename(file) + "]", size: 18, italics: true })] })],
  });
  const subtitle = H(4, `${num}. ${title}`, { keepNext: true });
  const descp = new Paragraph({ spacing: { after: 140, line: 360 }, alignment: AlignmentType.JUSTIFIED, keepNext: true, children: [new TextRun({ text: desc, font: "Times New Roman", size: 24 })] });
  const table = new Table({ width: { size: 100, type: WidthType.PERCENTAGE }, borders: noBorders, rows: [new TableRow({ cantSplit: true, children: [cell(wf), cell(mk)] })] });
  const cap = new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 60, after: 260 }, children: [new TextRun({ text: `Figura ${FIG}. ${title}. Wireframe (izquierda) y mockup (derecha).`, font: "Times New Roman", size: 20, italics: true })] });
  return [subtitle, descp, table, cap];
}

// ─── Pantallas ──────────────────────────────────────────────
const CIU = [
  ["C01", "Pantalla de bienvenida", "splash", "Es la primera pantalla que aparece al abrir la aplicación. Muestra el logotipo y el eslogan por unos segundos mientras el sistema carga y, a continuación, da paso al onboarding o, si el usuario ya había iniciado sesión, directamente al inicio."],
  ["C02", "Onboarding y guías de uso", "onboarding", "Presenta en pocos pasos qué hace la aplicación y cómo participar en la minka digital. Al terminar la guía, el usuario avanza hacia el inicio de sesión."],
  ["C03", "Inicio de sesión", "login", "Permite ingresar con el correo y la contraseña ya registrados. Si los datos son correctos, el usuario entra al inicio; desde aquí también puede ir al registro o a la recuperación de contraseña."],
  ["C04", "Registro de cuenta", "registro", "Reúne los datos necesarios para crear una cuenta nueva. Una vez completado el registro, la aplicación solicita los permisos que necesita para funcionar."],
  ["C05", "Recuperación de contraseña", "recuperar-contrasena", "Ofrece recuperar el acceso cuando se olvidó la contraseña, enviando un enlace al correo del usuario. Tras la confirmación, se regresa al inicio de sesión."],
  ["C06", "Solicitud de permisos", "permisos", "Solicita los permisos de cámara y ubicación, indispensables para fotografiar un punto crítico y situarlo en el mapa. Al concederlos, el usuario llega al inicio."],
  ["C07", "Inicio con mapa de calor", "inicio-mapa-calor", "Es la pantalla principal del ciudadano: resume su actividad y muestra un mapa de calor con las zonas más afectadas de la ciudad. Desde aquí puede explorar el mapa o iniciar un nuevo reporte con la cámara."],
  ["C08", "Exploración del mapa", "explorar-mapa", "Permite recorrer el mapa con mayor detalle, ubicar los reportes existentes y reconocer los puntos críticos cercanos antes de decidir dónde reportar."],
  ["C09", "Captura con la cámara", "camara-captura", "El ciudadano enfoca el punto de acumulación de residuos y toma la fotografía. Al capturarla, la aplicación la envía de inmediato al análisis automático."],
  ["C10", "Análisis con inteligencia artificial", "analisis-ia", "El modelo de inteligencia artificial procesa la imagen en el propio dispositivo y propone el tipo de residuo detectado. El usuario podrá confirmar o ajustar ese resultado en el siguiente paso."],
  ["C11", "Formulario del reporte", "formulario-reporte", "Completa el reporte con la ubicación, una breve descripción y el nivel de gravedad. Al enviarlo, la aplicación pasa a la pantalla de confirmación."],
  ["C12", "Confirmación y sincronización", "confirmacion-sync", "Confirma que el reporte quedó registrado y lo sincroniza con el servidor; si no hay conexión, se guarda y se envía cuando la haya. Luego, el usuario vuelve al inicio o a su historial."],
  ["C13", "Historial de reportes", "mis-reportes", "Reúne todos los reportes que el ciudadano ha enviado, con su estado actual. Se accede desde el menú principal y, al tocar cualquiera, se abre su detalle y seguimiento."],
  ["C14", "Estado vacío", "estado-vacio", "Es la versión del historial cuando el usuario todavía no ha enviado ningún reporte. En lugar de una lista vacía, lo invita a crear el primero."],
  ["C15", "Detalle y seguimiento", "detalle-seguimiento", "Muestra la información completa de un reporte y el avance de su atención, paso a paso. Se llega a ella desde el historial."],
  ["C16", "Notificaciones", "notificaciones", "Centraliza los avisos sobre los reportes del usuario, como los cambios de estado o los agradecimientos. Es una sección de consulta a la que se accede desde el menú."],
  ["C17", "Perfil y Minka digital", "perfil-minka", "Reúne el perfil del ciudadano y su progreso en la “Minka digital”: puntos, niveles e insignias que premian la participación y refuerzan el sentido comunitario del proyecto."],
  ["C18", "Configuración", "configuracion", "Permite ajustar la cuenta y las preferencias de la aplicación, como las notificaciones o el cierre de sesión. Es la última sección del menú del ciudadano."],
];
const ADM = [
  ["A01", "Panel del administrador", "panel-dashboard", "Es la pantalla de entrada del administrador. Resume en indicadores el estado general de los reportes y la actividad reciente, para ofrecer una visión rápida antes de operar."],
  ["A02", "Bandeja de alertas", "bandeja-alertas", "Lista los reportes entrantes ordenados por prioridad. El administrador elige uno para revisarlo y pasa a su detalle y validación."],
  ["A03", "Puntos críticos y agrupación espacial", "puntos-criticos-clustering", "Presenta los reportes sobre el mapa agrupados por cercanía, de modo que las zonas con mayor concentración resaltan. Ayuda a decidir dónde concentrar el trabajo."],
  ["A04", "Detalle y validación con IA", "detalle-validacion", "Permite revisar un reporte con el apoyo de la inteligencia artificial y decidir si se valida o se rechaza. Los reportes validados alimentan la planificación de rutas."],
  ["A05", "Planificación de rutas", "planificacion-rutas", "Organiza las rutas de recolección a partir de los puntos validados, optimizando el recorrido del personal de campo."],
];

// ─── Construcción del documento ─────────────────────────────
const c = [];

// ===== CAPÍTULO II: PRESUPUESTO =====
c.push(H(1, "CAPÍTULO II: PRESUPUESTO"));
c.push(p("El presupuesto de MinkIA contempla únicamente los desembolsos reales del equipo para construir y publicar la aplicación. Como el proyecto se apoya en herramientas gratuitas y de código abierto, el gasto se concentra en un solo rubro, la publicación en la tienda, como se detalla en el Cuadro 1."));

c.push(pCuadroTitle("Cuadro 1: Presupuesto del proyecto MinkIA (en soles)"));
c.push(new Table({
  width: { size: 100, type: WidthType.PERCENTAGE }, borders: tableBorders,
  rows: [
    new TableRow({ children: [th("Categoría", 26), th("Detalle", 54), th("Costo (S/)", 20)] }),
    new TableRow({ children: [td("Software y entorno de desarrollo"), tdLines(["Android Studio y Kotlin: desarrollo de la app (gratuitos y de código abierto)", "Python y YOLOv8: modelo de IA (gratuitos y de código abierto)"]), td("0.00", AlignmentType.CENTER)] }),
    new TableRow({ children: [td("Servicios en la nube"), tdLines(["Firebase Authentication: cuentas de usuario (plan gratuito Spark)", "Cloud Firestore: base de datos de reportes, con soporte sin conexión (plan gratuito Spark)", "Firebase Storage: almacenamiento de las fotografías (capa gratuita; ver nota)", "Firebase Cloud Messaging: notificaciones (gratuito)", "Google Maps Platform: mapa y mapa de calor (cuota gratuita mensual)"]), td("0.00", AlignmentType.CENTER)] }),
    new TableRow({ children: [td("Repositorio y control de versiones"), tdLines(["GitHub: trabajo colaborativo y control de versiones (plan gratuito)"]), td("0.00", AlignmentType.CENTER)] }),
    new TableRow({ children: [td("Conectividad y trabajo de campo"), tdLines(["Datos móviles del equipo", "Entrenamiento del modelo en equipos propios"]), td("0.00", AlignmentType.CENTER)] }),
    new TableRow({ children: [td("Publicación"), tdLines(["Google Play Console: cuenta de desarrollador (pago único de US$ 25)"]), td("95.00", AlignmentType.CENTER)] }),
    new TableRow({ children: [tdTop("Total estimado", AlignmentType.LEFT, true), tdTop("", AlignmentType.LEFT, false), tdTop("95.00", AlignmentType.CENTER, true)] }),
  ],
}));
c.push(pFuente());
c.push(p("Nota: tipo de cambio referencial de S/ 3.80 por dólar estadounidense.", { italics: true, size: 20, alignment: AlignmentType.LEFT }));

c.push(p("Los servicios en la nube operan dentro de sus capas gratuitas, suficientes para el prototipo. Al crecer el uso, con más reportes y fotografías almacenadas, Firebase (plan Blaze) y Google Maps pasan a pago por uso, con un costo variable según la adopción."));

c.push(p("El trabajo de los cinco integrantes (análisis, desarrollo, entrenamiento del modelo, diseño y pruebas) se asume como aporte académico y no se valoriza. En un proyecto comercial sería el rubro más costoso; aquí, gracias a las herramientas gratuitas, la inversión en infraestructura es casi nula."));

// ===== CAPÍTULO IV: PROTOTIPADO E IDENTIDAD DE MARCA =====
c.push(new Paragraph({ pageBreakBefore: true }));
c.push(H(1, "CAPÍTULO IV: PROTOTIPADO E IDENTIDAD DE MARCA"));

c.push(H(2, "4.1. Identidad de Marca"));
c.push(H(3, "4.1.1. Esencia y concepto"));
c.push(p("La identidad de MinkIA parte de una idea sencilla: unir un valor que ya existe en nuestra cultura con una herramienta moderna. Ese valor es la minka (también llamada minga), una práctica ancestral de raíz quechua en la que toda una comunidad se organiza de manera voluntaria para realizar un trabajo en beneficio de todos: levantar una casa, abrir un camino o recoger una cosecha. Nadie cobra por participar, porque el resultado le pertenece a la comunidad entera."));
c.push(p("MinkIA traslada esa idea al terreno digital. Cada vecino de Chimbote, desde su celular, pasa a formar parte de una minka digital orientada a cuidar el ambiente de su ciudad. En esta propuesta la inteligencia artificial cumple el papel de herramienta y la minka es el espíritu que la sostiene: la persona dirige y la tecnología acompaña. Por eso la marca no se presenta como “una aplicación para reportar basura”, sino como el rescate de una forma de trabajo comunitario aplicada a un problema actual. Esta lectura es coherente con la justificación social del proyecto, que busca convertir al vecino en un colaborador activo y no en un simple espectador."));

c.push(H(3, "4.1.2. El nombre"));
c.push(p("El nombre resume esa unión. MinkIA se forma con Mink(a) más IA, las siglas de inteligencia artificial. La escritura conserva visibles las dos partes: se lee “Minka” y, a la vez, resaltan las letras “IA”. El resultado es un nombre corto, fácil de recordar y con un significado arraigado en la identidad peruana."));

c.push(H(3, "4.1.3. Logotipo y símbolo"));
c.push(p("El logotipo es de tipo tipográfico y lleva un elemento simbólico integrado. La palabra “Mink” se escribe en verde bosque, color que asociamos con la naturaleza y el ambiente; el punto de la letra “i” se reemplaza por una hoja, que representa la vida y el crecimiento; y las letras “IA” se destacan en naranja terracota, que aporta energía y señala el componente de inteligencia artificial. La tipografía de trazos redondeados transmite cercanía y un trato amable, en línea con una herramienta pensada para el uso ciudadano."));
figura(FIG_PRINCIPAL, "Logotipo principal de MinkIA", 3.2).forEach(x => c.push(x));
c.push(p("El símbolo de la marca es una hoja que, al mismo tiempo, es un circuito. Sus nervaduras no son un adorno: trazan líneas que terminan en pequeños nodos, de modo que la hoja (la minka, la vida y el trabajo comunitario) y la tecnología (la inteligencia artificial, representada por los nodos) quedan unidas en una sola figura. Esta lectura resume la idea central del proyecto: la persona y su comunidad dirigen, y la tecnología acompaña. Por su forma sencilla y cerrada, el símbolo funciona también de manera autónoma como ícono de la aplicación."));
figura(FIG_ISOTIPO, "El símbolo de MinkIA: la hoja-circuito", 4.6).forEach(x => c.push(x));

c.push(H(3, "4.1.4. Versiones del logotipo"));
c.push(p("Una marca no se usa siempre en las mismas condiciones: a veces aparece sobre un fondo claro y otras sobre uno oscuro, en una impresión a una sola tinta o reducida al tamaño de un ícono. Para que se conserve reconocible en todos esos casos se definió un sistema de cuatro versiones oficiales, todas derivadas del mismo logotipo y símbolo a fin de mantener la coherencia de la marca."));
c.push(p("La versión principal, a color y sobre fondo claro, es la de uso preferente. El isotipo, la hoja-circuito aislada, se reserva para el ícono de la aplicación, el favicon y cualquier espacio reducido en el que el nombre completo no se leería bien. La versión monocromática, en una sola tinta, resuelve las impresiones en escala de grises y los documentos a un solo color. Por último, la versión sobre fondo oscuro invierte el texto a blanco y aclara la hoja, conservando el acento naranja para no perder contraste ni identidad."));
figura(FIG_VERSIONES, "Las cuatro versiones oficiales del logotipo", 6.2).forEach(x => c.push(x));
c.push(p("El uso correcto del símbolo se apoya, además, en dos reglas de construcción. El isotipo se traza sobre una retícula modular que fija las proporciones de la hoja y de sus nodos, y se rodea de un área de protección equivalente a una unidad de medida por cada lado, dentro de la cual ningún otro elemento gráfico puede ingresar. Estas reglas garantizan que la marca se reproduzca siempre de la misma forma y se mantenga legible en cualquier soporte."));
figura(FIG_CONSTRUCCION, "Construcción y área de protección del símbolo", 4.6).forEach(x => c.push(x));

c.push(H(3, "4.1.5. Paleta de colores"));
c.push(p("La paleta se apoya en dos familias de color. Los verdes comunican el eje ambiental y la esperanza, mientras que el naranja se reserva para las acciones principales y para señalar la inteligencia artificial. Los tonos neutros, blancos y grises, aportan orden y dejan respirar a la interfaz."));
c.push(pCuadroTitle("Cuadro 3: Paleta de colores de la marca"));
c.push(new Table({
  width: { size: 100, type: WidthType.PERCENTAGE }, borders: tableBorders,
  rows: [
    new TableRow({ children: [th("Color", 24), th("Código", 16), th("Muestra", 12), th("Significado", 48)] }),
    new TableRow({ children: [td("Verde bosque"), td("#1B4228", AlignmentType.CENTER), tdSwatch("1B4228"), td("Naturaleza y medio ambiente; representa aquello que se busca cuidar.")] }),
    new TableRow({ children: [td("Verde hoja"), td("#69802D", AlignmentType.CENTER), tdSwatch("69802D"), td("Vida, crecimiento y esperanza.")] }),
    new TableRow({ children: [td("Naranja terracota"), td("#BD4C18", AlignmentType.CENTER), tdSwatch("BD4C18"), td("Energía y acción; identifica el componente de inteligencia artificial.")] }),
  ],
}));
c.push(pFuente());

c.push(H(3, "4.1.6. Tipografía"));
c.push(p("Se eligió una familia sans serif de trazos redondeados (Poppins). Las curvas suaves dan una sensación de cercanía y se leen con comodidad en pantallas pequeñas, sin la frialdad de las tipografías más corporativas. Los títulos emplean un peso más marcado y el cuerpo de texto, un peso regular."));

c.push(H(3, "4.1.7. Tono y voz"));
c.push(p("La aplicación le habla al ciudadano de forma directa y cercana, y lo trata como protagonista. Cuando alguien envía un reporte recibe respuestas que reconocen su aporte, por ejemplo “tu barrio te lo agradece”, y un sistema de insignias que celebra la participación. La inteligencia artificial siempre aparece como un apoyo, nunca como un reemplazo de la persona."));

c.push(H(3, "4.1.8. Eslogan"));
c.push(p("El eslogan principal es: “Tu ciudad limpia empieza con un reporte”. Como variante se maneja “La minka, ahora digital”."));

c.push(H(2, "4.2. Prototipado"));
c.push(H(3, "4.2.1. Metodología de diseño"));
c.push(p("El diseño de las interfaces se hizo en dos etapas. Primero se trabajaron los wireframes, versiones en escala de grises que definen la estructura y la ubicación de cada elemento sin distraer con el color. Recién cuando la estructura quedó resuelta se elaboraron los mockups de alta fidelidad, que aplican la identidad visual completa: colores, tipografía, íconos y logotipo."));
c.push(p("En las páginas siguientes se presentan las dos versiones de cada pantalla, una al lado de la otra: a la izquierda el wireframe (la estructura) y a la derecha el mockup (el diseño final). Así se aprecia tanto el recorrido del diseño como la forma en que la marca se aplica sobre cada interfaz. La aplicación contempla dos perfiles de uso, que se muestran por separado: el del ciudadano, que reporta, y el del administrador, que gestiona y atiende esos reportes."));

c.push(H(3, "4.2.2. Flujo del ciudadano"));
c.push(p("El ciudadano es el centro de la minka digital. Su recorrido va desde el ingreso a la aplicación hasta el seguimiento de cada reporte, pasando por la captura de la foto y su análisis automático."));
CIU.forEach(([id, title, slug, desc], i) => par("ciudadano", id, title, slug, `4.2.2.${i + 1}`, desc).forEach(x => c.push(x)));

c.push(H(3, "4.2.3. Flujo del administrador"));
c.push(p("El administrador recibe los reportes, los valida y organiza el trabajo de recolección. Sus pantallas priorizan la lectura rápida de la información y la toma de decisiones."));
ADM.forEach(([id, title, slug, desc], i) => par("admin", id, title, slug, `4.2.3.${i + 1}`, desc).forEach(x => c.push(x)));

c.push(H(2, "4.2.4. Resumen de navegabilidad"));
c.push(p("Los siguientes esquemas resumen la navegación de la aplicación. Cada uno parte de la pantalla principal del perfil y muestra, con íconos y flechas, los grupos de pantallas a los que se llega desde ahí. Por su formato horizontal, se presentan en las páginas siguientes."));

// Contenido de la sección horizontal (landscape)
const resumen = [];
resumen.push(H(4, "4.2.4.1. Navegabilidad del ciudadano"));
figura(FIG_NAV_CIU, "Mapa de navegabilidad del ciudadano", 5.9).forEach(x => resumen.push(x));
resumen.push(H(4, "4.2.4.2. Navegabilidad del administrador", { pageBreakBefore: true }));
figura(FIG_NAV_ADM, "Mapa de navegabilidad del administrador", 8.6).forEach(x => resumen.push(x));

const mkFooter = () => new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ children: [PageNumber.CURRENT], font: "Times New Roman", size: 20 })] })] });

// ─── Estilos del documento ──────────────────────────────────
const headingRun = (size) => ({ size, bold: true, font: "Times New Roman", color: "000000", allCaps: true });
const doc = new Document({
  creator: "MinkIA", title: "MinkIA: Presupuesto, Prototipado e Identidad de Marca",
  styles: {
    default: {
      document: { run: { font: "Times New Roman", size: 24, color: "000000" } },
      paragraph: { run: { font: "Times New Roman", size: 24, color: "000000" }, paragraph: { alignment: "justified", spacing: { line: 360 } } },
    },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true, run: headingRun(28), paragraph: { alignment: "center", spacing: { before: 480, after: 240 }, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true, run: headingRun(24), paragraph: { alignment: "left", spacing: { before: 280, after: 160 }, outlineLevel: 1 } },
      { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true, run: headingRun(24), paragraph: { alignment: "left", spacing: { before: 220, after: 120 }, outlineLevel: 2 } },
      { id: "Heading4", name: "Heading 4", basedOn: "Normal", next: "Normal", quickFormat: true, run: headingRun(22), paragraph: { alignment: "left", spacing: { before: 200, after: 100 }, outlineLevel: 3 } },
    ],
  },
  sections: [
    {
      properties: { page: { size: { width: 12240, height: 15840 }, margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } } },
      footers: { default: mkFooter() },
      children: c,
    },
    {
      properties: { page: { size: { orientation: PageOrientation.LANDSCAPE, width: 15840, height: 12240 }, margin: { top: 1080, right: 1080, bottom: 1080, left: 1080 } } },
      footers: { default: mkFooter() },
      children: resumen,
    },
  ],
});

Packer.toBuffer(doc).then((buf) => {
  fs.writeFileSync(OUT, buf);
  console.log("OK ->", OUT, "(" + Math.round(buf.length / 1024) + " KB, " + FIG + " figuras)");
});
