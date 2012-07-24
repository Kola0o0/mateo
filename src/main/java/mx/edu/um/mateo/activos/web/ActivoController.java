/*
 * The MIT License
 *
 * Copyright 2012 Universidad de Montemorelos A. C.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package mx.edu.um.mateo.activos.web;

import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import mx.edu.um.mateo.activos.dao.ActivoDao;
import mx.edu.um.mateo.activos.dao.TipoActivoDao;
import mx.edu.um.mateo.activos.model.Activo;
import mx.edu.um.mateo.activos.model.BajaActivo;
import mx.edu.um.mateo.activos.model.ReubicacionActivo;
import mx.edu.um.mateo.activos.utils.ActivoNoCreadoException;
import mx.edu.um.mateo.contabilidad.model.Cuenta;
import mx.edu.um.mateo.general.model.Imagen;
import mx.edu.um.mateo.general.model.Usuario;
import mx.edu.um.mateo.general.utils.Constantes;
import mx.edu.um.mateo.general.utils.ReporteException;
import mx.edu.um.mateo.general.web.BaseController;
import org.apache.commons.lang.StringUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 *
 * @author J. David Mendoza <jdmendoza@um.edu.mx>
 */
@Controller
@RequestMapping("/activoFijo/activo")
public class ActivoController extends BaseController {

    @Autowired
    private ActivoDao activoDao;
    @Autowired
    private TipoActivoDao tipoActivoDao;

    @RequestMapping
    public String lista(HttpServletRequest request, HttpServletResponse response,
            @RequestParam(required = false) String filtro,
            @RequestParam(required = false) Long pagina,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String correo,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) String sort,
            Model modelo) throws ParseException {
        log.debug("Mostrando lista de activos");
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        Map<String, Object> params = this.convierteParams(request.getParameterMap());
        Long empresaId = (Long) request.getSession().getAttribute("empresaId");
        params.put("empresa", empresaId);

        if (params.containsKey("fechaIniciado")) {
            log.debug("FechaIniciado: {}", params.get("fechaIniciado"));
            params.put("fechaIniciado", sdf.parse((String) params.get("fechaIniciado")));
        }

        if (params.containsKey("fechaTerminado")) {
            params.put("fechaTerminado", sdf.parse((String) params.get("fechaTerminado")));
        }

        if (StringUtils.isNotBlank(tipo)) {
            params.put("reporte", true);
            params = activoDao.lista(params);
            try {
                generaReporte(tipo, (List<Activo>) params.get("activos"), response, "activos", Constantes.EMP, empresaId);
                return null;
            } catch (ReporteException e) {
                log.error("No se pudo generar el reporte", e);
            }
        }

        if (StringUtils.isNotBlank(correo)) {
            params.put("reporte", true);
            params = activoDao.lista(params);

            params.remove("reporte");
            try {
                enviaCorreo(correo, (List<Activo>) params.get("activos"), request, "activos", Constantes.EMP, empresaId);
                modelo.addAttribute("message", "lista.enviado.message");
                modelo.addAttribute("messageAttrs", new String[]{messageSource.getMessage("activo.lista.label", null, request.getLocale()), ambiente.obtieneUsuario().getUsername()});
            } catch (ReporteException e) {
                log.error("No se pudo enviar el reporte por correo", e);
            }
        }
        params = activoDao.lista(params);
        modelo.addAttribute("activos", params.get("activos"));
        modelo.addAttribute("resumen", params.get("resumen"));

        this.pagina(params, modelo, "activos", pagina);

        return "activoFijo/activo/lista";
    }

    @RequestMapping("/ver/{id}")
    public String ver(@PathVariable Long id, Model modelo) {
        log.debug("Mostrando activo {}", id);
        Activo activo = activoDao.obtiene(id);
        modelo.addAttribute("activo", activo);
        if (activo.getImagenes() != null & activo.getImagenes().size() > 0) {
            modelo.addAttribute("tieneImagenes", Boolean.TRUE);
        }

        return "activoFijo/activo/ver";
    }

    @RequestMapping("/nuevo")
    public String nuevo(HttpSession session, Model modelo) {
        log.debug("Nuevo activo");
        Long empresaId = (Long) session.getAttribute("empresaId");
        Long organizacionId = (Long) session.getAttribute("organizacionId");

        Activo activo = new Activo();
        modelo.addAttribute("activo", activo);

        List<String> motivos = new ArrayList<>();
        motivos.add("COMPRA");
        motivos.add("DONACION");
        modelo.addAttribute("motivos", motivos);

        Map<String, Object> params = new HashMap<>();
        params.put("empresa", empresaId);
        params.put("reporte", true);
        params = tipoActivoDao.lista(params);
        modelo.addAttribute("tiposDeActivo", params.get("tiposDeActivo"));

        List<Cuenta> cuentas = activoDao.cuentas(organizacionId);
        modelo.addAttribute("cuentas", cuentas);

        return "activoFijo/activo/nuevo";
    }

    @RequestMapping(value = "/crea", method = RequestMethod.POST)
    public String crea(HttpServletRequest request, HttpServletResponse response, @Valid Activo activo, BindingResult bindingResult, Errors errors, Model modelo, RedirectAttributes redirectAttributes, @RequestParam(value = "imagen", required = false) MultipartFile archivo) {
        for (String nombre : request.getParameterMap().keySet()) {
            log.debug("Param: {} : {}", nombre, request.getParameterMap().get(nombre));
        }
        if (bindingResult.hasErrors()) {
            log.debug("Hubo algun error en la forma, regresando");

            Long empresaId = (Long) request.getSession().getAttribute("empresaId");
            Long organizacionId = (Long) request.getSession().getAttribute("organizacionId");

            List<String> motivos = new ArrayList<>();
            motivos.add("COMPRA");
            motivos.add("DONACION");
            modelo.addAttribute("motivos", motivos);

            Map<String, Object> params = new HashMap<>();
            params.put("empresa", empresaId);
            params.put("reporte", true);
            params = tipoActivoDao.lista(params);
            modelo.addAttribute("tiposDeActivo", params.get("tiposDeActivo"));

            List<Cuenta> cuentas = activoDao.cuentas(organizacionId);
            modelo.addAttribute("cuentas", cuentas);

            return "activoFijo/activo/nuevo";
        }

        try {
            Usuario usuario = ambiente.obtieneUsuario();
            if (archivo != null && !archivo.isEmpty()) {
                Imagen imagen = new Imagen(
                        archivo.getOriginalFilename(),
                        archivo.getContentType(),
                        archivo.getSize(),
                        archivo.getBytes());
                activo.getImagenes().add(imagen);
            }
            log.debug("TipoActivo: {}", activo.getTipoActivo().getId());
            activo = activoDao.crea(activo, usuario);
        } catch (ConstraintViolationException | IOException e) {
            log.error("No se pudo crear al activo", e);
            errors.rejectValue("codigo", "campo.duplicado.message", new String[]{"codigo"}, null);

            Long empresaId = (Long) request.getSession().getAttribute("empresaId");
            Long organizacionId = (Long) request.getSession().getAttribute("organizacionId");

            List<String> motivos = new ArrayList<>();
            motivos.add("COMPRA");
            motivos.add("DONACION");
            modelo.addAttribute("motivos", motivos);

            Map<String, Object> params = new HashMap<>();
            params.put("empresa", empresaId);
            params.put("reporte", true);
            params = tipoActivoDao.lista(params);
            modelo.addAttribute("tiposDeActivo", params.get("tiposDeActivo"));

            List<Cuenta> cuentas = activoDao.cuentas(organizacionId);
            modelo.addAttribute("cuentas", cuentas);

            return "activoFijo/activo/nuevo";
        }

        redirectAttributes.addFlashAttribute("message", "activo.creado.message");
        redirectAttributes.addFlashAttribute("messageAttrs", new String[]{activo.getFolio()});

        return "redirect:/activoFijo/activo/ver/" + activo.getId();
    }

    @RequestMapping("/edita/{id}")
    public String edita(HttpServletRequest request, @PathVariable Long id, Model modelo) {
        log.debug("Edita activo {}", id);
        Activo activo = activoDao.obtiene(id);
        modelo.addAttribute("activo", activo);

        Map<String, Object> params = new HashMap<>();
        params.put("empresa", request.getSession().getAttribute("empresaId"));
        params.put("reporte", true);
        params = tipoActivoDao.lista(params);
        modelo.addAttribute("tiposDeActivo", params.get("tiposDeActivo"));

        return "activoFijo/activo/edita";
    }

    @RequestMapping(value = "/actualiza", method = RequestMethod.POST)
    public String actualiza(HttpServletRequest request, @Valid Activo activo, BindingResult bindingResult, Errors errors, Model modelo, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            log.error("Hubo algun error en la forma, regresando");

            Map<String, Object> params = new HashMap<>();
            params.put("empresa", request.getSession().getAttribute("empresaId"));
            params.put("reporte", true);
            params = tipoActivoDao.lista(params);
            modelo.addAttribute("tiposDeActivo", params.get("tiposDeActivo"));

            return "activoFijo/activo/edita";
        }

        try {
            Usuario usuario = ambiente.obtieneUsuario();
            activo = activoDao.actualiza(activo, usuario);
        } catch (ConstraintViolationException e) {
            log.error("No se pudo crear la activo", e);
            errors.rejectValue("nombre", "campo.duplicado.message", new String[]{"nombre"}, null);

            Map<String, Object> params = new HashMap<>();
            params.put("empresa", request.getSession().getAttribute("empresaId"));
            params.put("reporte", true);
            params = tipoActivoDao.lista(params);
            modelo.addAttribute("tiposDeActivo", params.get("tiposDeActivo"));

            return "activoFijo/activo/nuevo";
        }

        redirectAttributes.addFlashAttribute("message", "activo.actualizado.message");
        redirectAttributes.addFlashAttribute("messageAttrs", new String[]{activo.getFolio()});

        return "redirect:/activoFijo/activo/ver/" + activo.getId();
    }

    @RequestMapping(value = "/preparaBaja", method = RequestMethod.POST)
    public String preparaBaja(HttpServletRequest request, @RequestParam Long id, Model modelo) {
        log.debug("Preparando para de baja al activo {}", id);
        Activo activo = activoDao.obtiene(id);
        BajaActivo bajaActivo = new BajaActivo(activo, new Date());
        modelo.addAttribute("bajaActivo", bajaActivo);
        List<String> motivos = new ArrayList<>();
        motivos.add("OBSOLETO");
        motivos.add("PERDIDA");
        motivos.add("DONACION");
        motivos.add("VENTA");
        modelo.addAttribute("motivos", motivos);

        return "activoFijo/activo/baja";
    }

    @RequestMapping(value = "/baja", method = RequestMethod.POST)
    public String baja(Model modelo, @ModelAttribute BajaActivo bajaActivo, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        log.debug("Dando de baja al activo {}", bajaActivo.getActivo().getId());
        try {
            Usuario usuario = ambiente.obtieneUsuario();
            String nombre = activoDao.baja(bajaActivo, usuario);

            redirectAttributes.addFlashAttribute("message", "activo.baja.message");
            redirectAttributes.addFlashAttribute("messageAttrs", new String[]{nombre});
        } catch (Exception e) {
            log.error("No se pudo dar de baja al activo", e);
            bindingResult.addError(new ObjectError("activo", new String[]{"activo.no.baja.message"}, null, null));
            return "activoFijo/activo/ver";
        }

        return "redirect:/activoFijo/activo";
    }

    @RequestMapping("/arreglaFechas")
    public String arreglaFechas(RedirectAttributes redirectAttributes) {
        log.debug("Arreglando fechas");
        activoDao.arreglaFechas();
        redirectAttributes.addFlashAttribute("message", "activo.arregla.fechas");
        redirectAttributes.addFlashAttribute("messageStyle", "alert-success");
        return "redirect:/activoFijo/activo";
    }

    @RequestMapping(value = "/depreciar", method = RequestMethod.GET)
    public String preparaParaDepreciar() {
        return "activoFijo/activo/depreciar";
    }

    @RequestMapping(value = "/depreciar", method = RequestMethod.POST)
    public String depreciar(HttpSession session, @RequestParam String fecha, RedirectAttributes redirectAttributes) {
        log.debug("Depreciando activos");
        SimpleDateFormat sdf2 = new SimpleDateFormat("dd/MM/yyyy");
        Long empresaId = (Long) session.getAttribute("empresaId");
        Date fechaDepreciacion = new Date();
        try {
            fechaDepreciacion = sdf2.parse(fecha);
        } catch (ParseException e) {
            log.error("No se pudo convertir la fecha", e);
        }
        activoDao.depreciar(fechaDepreciacion, empresaId);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MMM/yyyy");
        redirectAttributes.addFlashAttribute("message", "activo.depreciar.message");
        redirectAttributes.addFlashAttribute("messageAttrs", new String[]{sdf.format(fechaDepreciacion)});
        redirectAttributes.addFlashAttribute("messageStyle", "alert-success");
        return "redirect:/activoFijo/activo";
    }

    @RequestMapping("/depreciar/{anio}/{mes}/{dia}")
    public String depreciarPorFecha(HttpSession session, @PathVariable Integer anio, @PathVariable Integer mes, @PathVariable Integer dia, RedirectAttributes redirectAttributes) {
        Long empresaId = (Long) session.getAttribute("empresaId");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, anio);
        cal.set(Calendar.MONTH, mes - 1);
        cal.set(Calendar.DAY_OF_MONTH, dia);
        Date fecha = cal.getTime();

        log.debug("Depreciando activos para la fecha {}", fecha);
        activoDao.depreciar(fecha, empresaId);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MMM/yyyy");
        redirectAttributes.addFlashAttribute("message", "activo.depreciar.message");
        redirectAttributes.addFlashAttribute("messageAttrs", new String[]{sdf.format(fecha)});
        redirectAttributes.addFlashAttribute("messageStyle", "alert-success");
        return "redirect:/activoFijo/activo";
    }

    @RequestMapping("/sube/{id}")
    public String sube(@PathVariable Long id, Model modelo) {
        Activo activo = activoDao.obtiene(id);
        modelo.addAttribute("activo", activo);
        return "activoFijo/activo/sube";
    }

    @RequestMapping("/subeImagen")
    public String subeImagen(@RequestParam Long activoId, @RequestParam(value = "imagen", required = false) MultipartFile archivo, RedirectAttributes redirectAttributes) {
        log.debug("Subiendo imagen para activo {}", activoId);
        try {
            if (archivo != null && !archivo.isEmpty()) {
                Usuario usuario = ambiente.obtieneUsuario();
                Activo activo = activoDao.obtiene(activoId);
                Imagen imagen = new Imagen(
                        archivo.getOriginalFilename(),
                        archivo.getContentType(),
                        archivo.getSize(),
                        archivo.getBytes());
                activo.getImagenes().add(imagen);
                activoDao.subeImagen(activo, usuario);
            }
            redirectAttributes.addFlashAttribute("message", "activo.sube.imagen.message");
            redirectAttributes.addFlashAttribute("messageStyle", "alert-success");
        } catch (IOException e) {
            log.error("Hubo un problema al intentar subir la imagen del activo", e);
        }
        return "redirect:/activoFijo/activo/ver/" + activoId;
    }

    @RequestMapping(value = "/reubica/{id}", method = RequestMethod.GET)
    public String preparaReubicacion(@PathVariable Long id, Model modelo) {
        Activo activo = activoDao.obtiene(id);
        ReubicacionActivo reubicacion = new ReubicacionActivo(activo, new Date());
        modelo.addAttribute("reubicacion", reubicacion);
        return "activoFijo/activo/reubica";
    }

    @RequestMapping(value = "/reubica", method = RequestMethod.POST)
    public String reubica(Model modelo, @ModelAttribute ReubicacionActivo reubicacion, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "activoFijo/activo/reubica/" + reubicacion.getActivo().getId();
        }

        Usuario usuario = ambiente.obtieneUsuario();
        String nombre = activoDao.reubica(reubicacion, usuario);
        redirectAttributes.addFlashAttribute("message", "activo.reubica.message");
        redirectAttributes.addFlashAttribute("messageAttrs", new String[]{nombre});

        return "redirect:/activoFijo/activo/ver/" + reubicacion.getActivo().getId();
    }

    @RequestMapping(value = "/subeActivos", method = RequestMethod.GET)
    public String preparaParaSubir() {
        return "/activoFijo/activo/subeActivos";
    }

    @RequestMapping(value = "/subeActivos", method = RequestMethod.POST)
    public String sube(HttpServletResponse response, RedirectAttributes redirectAttributes, MultipartFile archivo, @RequestParam Integer codigo) throws IOException, ActivoNoCreadoException {
        redirectAttributes.addFlashAttribute("message", "activo.sube.archivo.message");
        response.setContentType("application/vnd.ms-excel");
        response.setHeader("Content-disposition","attachment; filename='errores-"+archivo.getOriginalFilename()+"'");
        OutputStream out = response.getOutputStream();
        activoDao.sube(archivo.getBytes(), ambiente.obtieneUsuario(), response.getOutputStream(), codigo);
        out.flush();

        return "redirect:/activoFijo/activo";
    }
}
